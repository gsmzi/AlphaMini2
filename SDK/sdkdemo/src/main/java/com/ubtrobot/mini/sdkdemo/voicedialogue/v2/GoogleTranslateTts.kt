package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Free online TTS using Google Translate's undocumented endpoint.
 * Returns MP3 audio bytes. No API key needed.
 * Works for short texts (max ~200 chars per request; auto-splits longer texts).
 */
class GoogleTranslateTts {
    companion object {
        private const val TAG = "GoogleTts"
        private const val MAX_CHUNK_CHARS = 180
        private const val BASE_URL = "https://translate.google.com/translate_tts"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Synthesize [text] in [languageCode] ("de" or "en") to MP3 bytes.
     * Returns null on failure.
     */
    suspend fun synthesize(text: String, languageCode: String): ByteArray? {
        if (text.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val t0 = System.currentTimeMillis()

                val result = if (text.length <= MAX_CHUNK_CHARS) {
                    fetchChunk(text, languageCode)
                } else {
                    val chunks = splitIntoChunks(text, MAX_CHUNK_CHARS)
                    val output = ByteArrayOutputStream()
                    for (chunk in chunks) {
                        val mp3 = fetchChunk(chunk, languageCode) ?: continue
                        output.write(mp3)
                    }
                    val bytes = output.toByteArray()
                    if (bytes.isEmpty()) null else bytes
                }

                val elapsed = System.currentTimeMillis() - t0
                Log.d(TAG, "Synthesized '${text.take(50)}...' [${languageCode}] → ${result?.size ?: 0} bytes in ${elapsed}ms")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Synthesis failed for '${text.take(50)}'", e)
                null
            }
        }
    }

    private fun fetchChunk(text: String, lang: String): ByteArray? {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "$BASE_URL?ie=UTF-8&q=$encoded&tl=$lang&client=tw-ob"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11)")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "HTTP ${response.code} for chunk '${text.take(30)}'")
            response.close()
            return null
        }
        return response.body?.bytes()
    }

    private fun splitIntoChunks(text: String, maxLen: Int): List<String> {
        // Split on sentence boundaries first
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (sentence in sentences) {
            if (current.length + sentence.length + 1 > maxLen && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.clear()
            }
            if (current.isNotEmpty()) current.append(" ")
            current.append(sentence)
        }
        if (current.isNotBlank()) {
            chunks.add(current.toString().trim())
        }
        return chunks
    }
}
