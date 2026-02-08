package com.ubtrobot.mini.sdkdemo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.google.gson.Gson
import com.ubtrobot.master.component.ResourcePolicy
import com.ubtrobot.mini.voice.VoiceListener
import com.ubtrobot.mini.voice.VoicePool
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
 * Edge TTS Client
 * Connects to a Python Edge TTS server for free text-to-speech
 * No API key required!
 */
class EdgeTTS(private val context: Context, private val serverUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var currentAudioFile: File? = null
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "EdgeTTS"
    }

    // Request data class
    data class TTSRequest(
        val text: String,
        val language: String,
        val gender: String = "male"
    )

    /**
     * Speak text using Edge TTS server
     * @param text The text to speak
     * @param languageCode Language code (e.g., "en-US", "de-DE")
     * @param onComplete Callback when speech completes
     * @param onError Callback on error
     */
    suspend fun speak(
        text: String,
        languageCode: String = "en-US",
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            Log.d(TAG, "Speaking: '$text' in $languageCode")

            // Build request
            val request = TTSRequest(
                text = text,
                language = languageCode,
                gender = "male"
            )

            val jsonBody = gson.toJson(request)

            // Make API call
            val audioBytes = withContext(Dispatchers.IO) {
                callTTSServer(jsonBody)
            }

            if (audioBytes != null) {
                playAudio(audioBytes, onComplete, onError)
            } else {
                onError?.invoke("Failed to get audio from server")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error speaking: ${e.message}", e)
            onError?.invoke(e.message ?: "Unknown error")
        }
    }

    private fun callTTSServer(jsonBody: String): ByteArray? {
        val url = "$serverUrl/tts"

        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                Log.e(TAG, "Server error: ${response.code} - ${response.body?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}", e)
            null
        }
    }

    private suspend fun playAudio(
        audioBytes: ByteArray,
        onComplete: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        try {
            // Save to external cache directory (accessible by VoicePool service)
            val ttsDir = context.externalCacheDir ?: context.cacheDir
            val tempFile = File(ttsDir, "tts_audio.mp3")

            withContext(Dispatchers.IO) {
                FileOutputStream(tempFile).use { it.write(audioBytes) }
                // Make readable by other processes (VoicePool service)
                tempFile.setReadable(true, false)
                tempFile.setWritable(true, false)
            }

            Log.d(TAG, "Audio saved to: ${tempFile.absolutePath}")

            // Stop any existing playback
            stopSpeaking()

            // Store reference for cleanup
            currentAudioFile = tempFile

            // Try VoicePool first (robot's speaker)
            withContext(Dispatchers.Main) {
                try {
                    VoicePool.get().playLocalTTs(tempFile, ResourcePolicy.Exclusive, object : VoiceListener {
                        override fun onCompleted() {
                            Log.d(TAG, "VoicePool playback completed")
                            currentAudioFile = null
                            onComplete?.invoke()
                        }

                        override fun onError(code: Int, message: String) {
                            Log.e(TAG, "VoicePool error: $code - $message, falling back to MediaPlayer")
                            // Fallback to MediaPlayer with speaker stream
                            playWithMediaPlayer(tempFile, onComplete, onError)
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "VoicePool failed: ${e.message}, using MediaPlayer")
                    playWithMediaPlayer(tempFile, onComplete, onError)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio: ${e.message}", e)
            onError?.invoke(e.message ?: "Playback error")
        }
    }

    private fun playWithMediaPlayer(
        file: File,
        onComplete: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        try {
            // Check if file still exists
            if (!file.exists()) {
                Log.e(TAG, "Audio file doesn't exist: ${file.absolutePath}")
                onError?.invoke("Audio file not found")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    Log.d(TAG, "MediaPlayer playback completed")
                    file.delete()
                    currentAudioFile = null
                    onComplete?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: $what, $extra")
                    file.delete()
                    currentAudioFile = null
                    onError?.invoke("Playback error: $what")
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer setup failed: ${e.message}", e)
            onError?.invoke("MediaPlayer error: ${e.message}")
        }
    }

    /**
     * Stop any ongoing speech
     */
    fun stopSpeaking() {
        try {
            // Stop VoicePool
            VoicePool.get().stopTTs(ResourcePolicy.Exclusive, null)

            // Stop MediaPlayer
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            currentAudioFile?.delete()
            currentAudioFile = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech: ${e.message}")
        }
    }

    /**
     * Check if server is available
     */
    suspend fun isServerAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverUrl/health")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Server check failed: ${e.message}")
                false
            }
        }
    }

    /**
     * Clean up resources
     */
    fun release() {
        stopSpeaking()
    }
}
