package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Free TTS using Microsoft Edge's speech synthesis WebSocket endpoint.
 * Returns MP3 audio bytes. No API key needed.
 * Young male voices: en-US-GuyNeural (English), de-DE-ConradNeural (German).
 */
class EdgeTts {
    companion object {
        private const val TAG = "EdgeTts"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val BASE_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
        private const val SEC_MS_GEC_VERSION = "1-143.0.3650.75"
        // Windows epoch offset: seconds between 1601-01-01 and 1970-01-01
        private const val WIN_EPOCH = 11644473600L

        private val VOICES = mapOf(
            "en" to "en-US-GuyNeural",
            "de" to "de-DE-ConradNeural"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Synthesize [text] in [languageCode] ("de" or "en") to MP3 bytes.
     * Drop-in replacement for GoogleTranslateTts.synthesize().
     */
    suspend fun synthesize(text: String, languageCode: String): ByteArray? {
        if (text.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val t0 = System.currentTimeMillis()
                val voice = VOICES[languageCode] ?: VOICES["en"]!!
                val result = doSynthesize(text, voice)
                val elapsed = System.currentTimeMillis() - t0
                Log.d(TAG, "Synthesized '${text.take(50)}...' [$languageCode, $voice] → ${result?.size ?: 0} bytes in ${elapsed}ms")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Synthesis failed for '${text.take(50)}'", e)
                null
            }
        }
    }

    /**
     * Generate the Sec-MS-GEC token (SHA-256 of rounded Windows file time + trusted token).
     */
    private fun generateSecMsGec(): String {
        val nowSecs = System.currentTimeMillis() / 1000
        var ticks = nowSecs + WIN_EPOCH
        ticks -= ticks % 300  // round down to nearest 5 minutes
        ticks *= 10_000_000   // convert to 100-nanosecond intervals
        val input = "$ticks$TRUSTED_CLIENT_TOKEN"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }

    private suspend fun doSynthesize(text: String, voice: String): ByteArray? {
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val secMsGec = generateSecMsGec()
        val url = "$BASE_URL?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
            "&ConnectionId=$connectionId" +
            "&Sec-MS-GEC=$secMsGec" +
            "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

        return suspendCancellableCoroutine { cont ->
            val audioBuffer = ByteArrayOutputStream()

            val request = Request.Builder()
                .url(url)
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // 1) Send speech config
                    val configMsg = "X-Timestamp:${getTimestamp()}\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"$OUTPUT_FORMAT"}}}}"""
                    webSocket.send(configMsg)

                    // 2) Send SSML
                    val escapedText = text
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&apos;")

                    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                        "<voice name='$voice'>$escapedText</voice></speak>"

                    val ssmlMsg = "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${getTimestamp()}\r\n" +
                        "Path:ssml\r\n\r\n$ssml"
                    webSocket.send(ssmlMsg)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    // Binary message: first 2 bytes = header length (big-endian), then header, then audio
                    val data = bytes.toByteArray()
                    if (data.size < 2) return

                    val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    if (data.size > headerLen + 2) {
                        audioBuffer.write(data, headerLen + 2, data.size - headerLen - 2)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        webSocket.close(1000, "done")
                        val result = audioBuffer.toByteArray()
                        if (cont.isActive) {
                            cont.resume(if (result.isNotEmpty()) result else null)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}")
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (cont.isActive) {
                        val result = audioBuffer.toByteArray()
                        cont.resume(if (result.isNotEmpty()) result else null)
                    }
                }
            })

            cont.invokeOnCancellation {
                ws.cancel()
            }
        }
    }

    private fun getTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }
}
