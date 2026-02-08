package com.ubtrobot.mini.sdkdemo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Google Cloud Text-to-Speech helper class
 * Uses the REST API to convert text to speech
 */
class GoogleCloudTTS(private val context: Context, private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "GoogleCloudTTS"
        private const val TTS_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
    }

    // Request data classes
    data class TTSRequest(
        val input: Input,
        val voice: Voice,
        val audioConfig: AudioConfig
    )

    data class Input(
        val text: String
    )

    data class Voice(
        val languageCode: String,
        val name: String? = null,
        val ssmlGender: String = "NEUTRAL"
    )

    data class AudioConfig(
        val audioEncoding: String = "MP3",
        val speakingRate: Double = 1.0,
        val pitch: Double = 0.0
    )

    // Response data class
    data class TTSResponse(
        @SerializedName("audioContent")
        val audioContent: String
    )

    /**
     * Speak text using Google Cloud TTS
     * @param text The text to speak
     * @param languageCode Language code (e.g., "en-US", "de-DE")
     * @param voiceName Optional specific voice name
     * @param onComplete Callback when speech completes
     * @param onError Callback on error
     */
    suspend fun speak(
        text: String,
        languageCode: String = "en-US",
        voiceName: String? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            // Build request
            val request = TTSRequest(
                input = Input(text = text),
                voice = Voice(
                    languageCode = languageCode,
                    name = voiceName ?: getDefaultVoice(languageCode),
                    ssmlGender = "NEUTRAL"
                ),
                audioConfig = AudioConfig(
                    audioEncoding = "MP3",
                    speakingRate = 1.0,
                    pitch = 0.0
                )
            )

            val jsonBody = gson.toJson(request)
            Log.d(TAG, "Request: $jsonBody")

            // Make API call
            val audioContent = withContext(Dispatchers.IO) {
                callTTSApi(jsonBody)
            }

            if (audioContent != null) {
                // Decode base64 audio and play
                playAudio(audioContent, onComplete, onError)
            } else {
                onError?.invoke("Failed to get audio from API")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error speaking: ${e.message}", e)
            onError?.invoke(e.message ?: "Unknown error")
        }
    }

    private fun callTTSApi(jsonBody: String): String? {
        val url = "$TTS_URL?key=$apiKey"

        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Response: $responseBody")
                val ttsResponse = gson.fromJson(responseBody, TTSResponse::class.java)
                ttsResponse.audioContent
            } else {
                Log.e(TAG, "API error: ${response.code} - ${response.body?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}", e)
            null
        }
    }

    private suspend fun playAudio(
        base64Audio: String,
        onComplete: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        try {
            // Decode base64 to bytes
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)

            // Save to temp file
            val tempFile = File(context.cacheDir, "tts_audio_${System.currentTimeMillis()}.mp3")
            withContext(Dispatchers.IO) {
                FileOutputStream(tempFile).use { it.write(audioBytes) }
            }

            // Stop any existing playback
            stopSpeaking()

            // Play audio
            withContext(Dispatchers.Main) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(tempFile.absolutePath)
                    setOnCompletionListener {
                        tempFile.delete()
                        onComplete?.invoke()
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: $what, $extra")
                        tempFile.delete()
                        onError?.invoke("Playback error: $what")
                        true
                    }
                    prepare()
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio: ${e.message}", e)
            onError?.invoke(e.message ?: "Playback error")
        }
    }

    /**
     * Stop any ongoing speech
     */
    fun stopSpeaking() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    /**
     * Get default voice for language
     */
    private fun getDefaultVoice(languageCode: String): String {
        return when (languageCode) {
            "en-US" -> "en-US-Wavenet-D"  // Male voice
            "en-GB" -> "en-GB-Wavenet-B"
            "de-DE" -> "de-DE-Wavenet-B"  // German male
            "fr-FR" -> "fr-FR-Wavenet-B"
            "es-ES" -> "es-ES-Wavenet-B"
            "it-IT" -> "it-IT-Wavenet-A"
            "ja-JP" -> "ja-JP-Wavenet-C"
            "zh-CN" -> "zh-CN-Wavenet-B"
            else -> "${languageCode}-Wavenet-A"
        }
    }

    /**
     * Clean up resources
     */
    fun release() {
        stopSpeaking()
    }
}
