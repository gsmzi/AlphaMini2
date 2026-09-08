package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * TTS Client Interface
 */
interface ITTSClient {
    suspend fun synthesize(text: String, language: String): ByteArray?
    suspend fun synthesizeStreaming(text: String, language: String, onChunk: (ByteArray) -> Unit): Boolean
    suspend fun isAvailable(): Boolean
}

/**
 * Edge TTS Client
 * Connects to the Python Edge TTS server for text-to-speech conversion.
 */
class TTSClient(
    private val config: DialogueConfig
) : ITTSClient {
    companion object {
        private const val TAG = "TTSClient"
        private const val ENDPOINT_TTS = "/tts"
        private const val ENDPOINT_HEALTH = "/health"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.networkConnectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.ttsTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()

    data class TTSRequest(
        val text: String,
        val language: String,
        val gender: String = "male"
    )

    override suspend fun synthesize(text: String, language: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(config.ttsTimeoutMs) {
                    Log.d(TAG, "Synthesizing: '${text.take(50)}...' in $language")

                    val request = TTSRequest(
                        text = text,
                        language = language
                    )

                    val jsonBody = gson.toJson(request)
                    val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                    val httpRequest = Request.Builder()
                        .url("${config.ttsServerUrl}$ENDPOINT_TTS")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(httpRequest).execute()

                    if (response.isSuccessful) {
                        val audioData = response.body?.bytes()
                        Log.d(TAG, "TTS successful: ${audioData?.size ?: 0} bytes")
                        audioData
                    } else {
                        Log.e(TAG, "TTS error: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS synthesis failed", e)
                null
            }
        }
    }

    /**
     * Streaming TTS for incremental playback
     * Synthesizes sentence by sentence and calls onChunk for each
     */
    override suspend fun synthesizeStreaming(
        text: String,
        language: String,
        onChunk: (ByteArray) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Split text into sentences for incremental TTS
                val sentences = SpeechFormatter(
                    if (language.startsWith("de")) DialogueConfig.Language.DE
                    else DialogueConfig.Language.EN
                ).splitIntoSentences(text)

                Log.d(TAG, "Streaming TTS: ${sentences.size} sentences")

                for (sentence in sentences) {
                    if (sentence.isBlank()) continue

                    val audioData = synthesize(sentence, language)
                    if (audioData != null) {
                        onChunk(audioData)
                    } else {
                        Log.w(TAG, "Failed to synthesize sentence: $sentence")
                    }
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "Streaming TTS failed", e)
                false
            }
        }
    }

    override suspend fun isAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${config.ttsServerUrl}$ENDPOINT_HEALTH")
                    .get()
                    .build()

                client.newCall(request).execute().isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "TTS health check failed", e)
                false
            }
        }
    }
}

/**
 * TTS with local caching for frequently used phrases
 */
class CachedTTSClient(
    private val delegate: ITTSClient,
    private val maxCacheSize: Int = 50
) : ITTSClient {
    companion object {
        private const val TAG = "CachedTTSClient"
    }

    private val cache = object : LinkedHashMap<String, ByteArray>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean {
            return size > maxCacheSize
        }
    }

    override suspend fun synthesize(text: String, language: String): ByteArray? {
        val key = "$language:$text"

        // Check cache first
        cache[key]?.let {
            Log.d(TAG, "TTS cache hit: $key")
            return it
        }

        // Synthesize and cache
        val result = delegate.synthesize(text, language)
        if (result != null) {
            synchronized(cache) {
                cache[key] = result
            }
        }
        return result
    }

    override suspend fun synthesizeStreaming(
        text: String,
        language: String,
        onChunk: (ByteArray) -> Unit
    ): Boolean {
        return delegate.synthesizeStreaming(text, language, onChunk)
    }

    override suspend fun isAvailable(): Boolean {
        return delegate.isAvailable()
    }

    /**
     * Pre-cache common phrases
     */
    suspend fun preloadCommonPhrases(language: String) {
        val phrases = if (language.startsWith("de")) {
            listOf(
                "Ich höre zu.",
                "Einen Moment bitte.",
                "Das habe ich nicht verstanden.",
                "Entschuldigung, ein Fehler ist aufgetreten.",
                "Wie kann ich dir helfen?",
                "Gerne!",
                "Tschüss!"
            )
        } else {
            listOf(
                "I'm listening.",
                "One moment please.",
                "I didn't catch that.",
                "Sorry, an error occurred.",
                "How can I help you?",
                "You're welcome!",
                "Goodbye!"
            )
        }

        for (phrase in phrases) {
            synthesize(phrase, language)
        }

        Log.d(TAG, "Preloaded ${phrases.size} common phrases")
    }

    fun clearCache() {
        synchronized(cache) {
            cache.clear()
        }
    }
}
