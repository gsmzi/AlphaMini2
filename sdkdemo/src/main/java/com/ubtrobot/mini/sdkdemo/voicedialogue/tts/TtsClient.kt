package com.ubtrobot.mini.sdkdemo.voicedialogue.tts

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * TTS Voice configuration
 */
data class TtsVoice(
    val languageCode: String,
    val voiceName: String,
    val gender: String = "male"
)

/**
 * TTS Client interface
 */
interface TtsClient {
    /**
     * Synthesize text to speech
     * @param text Text to synthesize
     * @param languageCode Language code (e.g., "en-US", "de-DE")
     * @return Audio data as bytes (MP3 format)
     */
    suspend fun synthesize(text: String, languageCode: String = "en-US"): ByteArray?

    /**
     * Get available voices
     */
    suspend fun getVoices(): List<TtsVoice>

    /**
     * Check if TTS service is available
     */
    suspend fun isAvailable(): Boolean

    /**
     * Release resources
     */
    fun release()
}

/**
 * Edge TTS Client implementation
 * Connects to Python Edge TTS server for free text-to-speech
 * Supports English (en-US) and German (de-DE)
 */
class EdgeTtsClient(
    private val serverUrl: String
) : TtsClient {
    companion object {
        private const val TAG = "EdgeTtsClient"
        private const val ENDPOINT_TTS = "/tts"
        private const val ENDPOINT_VOICES = "/voices"
        private const val ENDPOINT_HEALTH = "/health"

        // Default voices for each language
        private val DEFAULT_VOICES = mapOf(
            "en-US" to TtsVoice("en-US", "en-US-GuyNeural", "male"),
            "en-GB" to TtsVoice("en-GB", "en-GB-RyanNeural", "male"),
            "de-DE" to TtsVoice("de-DE", "de-DE-ConradNeural", "male"),
            "de-AT" to TtsVoice("de-AT", "de-AT-JonasNeural", "male")
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)  // Faster connection
        .readTimeout(30, TimeUnit.SECONDS)     // Faster read timeout
        .build()

    private val gson = Gson()

    // Request DTO matching the Edge TTS server
    data class TtsRequest(
        val text: String,
        val language: String,
        val gender: String = "male"
    )

    override suspend fun synthesize(text: String, languageCode: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Synthesizing: '${text.take(50)}...' in $languageCode")

                val request = TtsRequest(
                    text = text,
                    language = languageCode,
                    gender = DEFAULT_VOICES[languageCode]?.gender ?: "male"
                )

                val jsonBody = gson.toJson(request)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$serverUrl$ENDPOINT_TTS")
                    .post(requestBody)
                    .build()

                val response = client.newCall(httpRequest).execute()

                if (response.isSuccessful) {
                    val audioBytes = response.body?.bytes()
                    Log.d(TAG, "TTS synthesis successful: ${audioBytes?.size ?: 0} bytes")
                    audioBytes
                } else {
                    Log.e(TAG, "TTS error: ${response.code} - ${response.body?.string()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS synthesis failed", e)
                null
            }
        }
    }

    override suspend fun getVoices(): List<TtsVoice> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverUrl$ENDPOINT_VOICES")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    // Parse voice list from server
                    // For now, return default voices
                    DEFAULT_VOICES.values.toList()
                } else {
                    DEFAULT_VOICES.values.toList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get voices", e)
                DEFAULT_VOICES.values.toList()
            }
        }
    }

    override suspend fun isAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverUrl$ENDPOINT_HEALTH")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "TTS health check failed", e)
                false
            }
        }
    }

    override fun release() {
        // No resources to release for HTTP client
    }
}

/**
 * Multi-language TTS Client wrapper
 * Manages multiple TTS engines for different languages
 */
class MultiLanguageTtsClient(
    private val primaryClient: TtsClient
) : TtsClient {
    companion object {
        private const val TAG = "MultiLanguageTtsClient"
    }

    // Cache for language-specific clients if needed
    private val languageClients = mutableMapOf<String, TtsClient>()

    init {
        // Use primary client for all languages by default
        languageClients["en-US"] = primaryClient
        languageClients["en-GB"] = primaryClient
        languageClients["de-DE"] = primaryClient
        languageClients["de-AT"] = primaryClient
    }

    override suspend fun synthesize(text: String, languageCode: String): ByteArray? {
        val client = languageClients[languageCode] ?: primaryClient
        return client.synthesize(text, languageCode)
    }

    override suspend fun getVoices(): List<TtsVoice> {
        return primaryClient.getVoices()
    }

    override suspend fun isAvailable(): Boolean {
        return primaryClient.isAvailable()
    }

    override fun release() {
        primaryClient.release()
        languageClients.values.forEach { it.release() }
        languageClients.clear()
    }

    /**
     * Register a custom TTS client for a specific language
     */
    fun registerLanguageClient(languageCode: String, client: TtsClient) {
        languageClients[languageCode] = client
    }
}

/**
 * Utility functions for TTS
 */
object TtsUtils {
    /**
     * Get the best voice for a language code
     */
    fun getBestVoice(languageCode: String, gender: String = "male"): TtsVoice {
        return when {
            languageCode.startsWith("en") -> TtsVoice(languageCode, "en-US-GuyNeural", gender)
            languageCode.startsWith("de") -> TtsVoice(languageCode, "de-DE-ConradNeural", gender)
            else -> TtsVoice("en-US", "en-US-GuyNeural", gender)
        }
    }

    /**
     * Normalize language code to supported format
     */
    fun normalizeLanguageCode(code: String): String {
        val lower = code.lowercase()
        return when {
            lower == "en" || lower.startsWith("en-") -> "en-US"
            lower == "de" || lower.startsWith("de-") -> "de-DE"
            else -> "en-US"
        }
    }
}
