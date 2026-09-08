package com.ubtrobot.mini.sdkdemo.voicedialogue.llm

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
import java.util.concurrent.TimeUnit

/**
 * Intent tag from LLM response for robot behavior
 */
data class IntentTag(
    val type: String,           // "emotion", "action", "light", "motor"
    val value: String,          // e.g., "happy", "wave", "blue"
    val timing: String? = null, // "start", "end", "during"
    val duration: Long? = null  // duration in ms
)

/**
 * LLM Response structure
 */
data class LlmResponse(
    /**
     * Text response (if model returns text)
     */
    val responseText: String? = null,

    /**
     * Audio response bytes (if model returns audio directly)
     */
    val responseAudio: ByteArray? = null,

    /**
     * Intent tags for robot behavior
     */
    val intentTags: List<IntentTag> = emptyList(),

    /**
     * Detected language from ASR or model
     */
    val detectedLanguage: String? = null,

    /**
     * Transcription of user's speech (if ASR was used)
     */
    val transcription: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LlmResponse
        if (responseText != other.responseText) return false
        if (responseAudio != null) {
            if (other.responseAudio == null) return false
            if (!responseAudio.contentEquals(other.responseAudio)) return false
        } else if (other.responseAudio != null) return false
        if (intentTags != other.intentTags) return false
        if (detectedLanguage != other.detectedLanguage) return false
        if (transcription != other.transcription) return false
        return true
    }

    override fun hashCode(): Int {
        var result = responseText?.hashCode() ?: 0
        result = 31 * result + (responseAudio?.contentHashCode() ?: 0)
        result = 31 * result + intentTags.hashCode()
        result = 31 * result + (detectedLanguage?.hashCode() ?: 0)
        result = 31 * result + (transcription?.hashCode() ?: 0)
        return result
    }
}

/**
 * LLM Client interface for model communication
 */
interface LlmClient {
    /**
     * Send text to LLM and get response
     */
    suspend fun sendText(text: String, languageInstruction: String? = null): LlmResponse?

    /**
     * Send audio to LLM (for speech-to-speech or ASR+LLM)
     */
    suspend fun sendAudio(audioData: ByteArray, languageInstruction: String? = null): LlmResponse?

    /**
     * Check if LLM service is available
     */
    suspend fun isAvailable(): Boolean
}

/**
 * HTTP-based LLM Client implementation
 * Connects to a REST API endpoint for LLM inference
 */
class HttpLlmClient(
    private val serverUrl: String,
    private val apiKey: String? = null,
    private val modelId: String = "default"
) : LlmClient {
    companion object {
        private const val TAG = "HttpLlmClient"
        private const val ENDPOINT_CHAT = "/v1/chat"
        private const val ENDPOINT_AUDIO = "/v1/audio/chat"
        private const val ENDPOINT_HEALTH = "/health"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)  // Faster connection timeout
        .readTimeout(30, TimeUnit.SECONDS)     // Reduced read timeout for faster feedback
        .build()

    private val gson = Gson()

    // Request/Response DTOs
    data class ChatRequest(
        val message: String,
        val model: String? = null,
        @SerializedName("system_prompt")
        val systemPrompt: String? = null,
        @SerializedName("language_instruction")
        val languageInstruction: String? = null
    )

    data class AudioChatRequest(
        @SerializedName("audio_base64")
        val audioBase64: String,
        val model: String? = null,
        @SerializedName("language_instruction")
        val languageInstruction: String? = null,
        @SerializedName("return_audio")
        val returnAudio: Boolean = false
    )

    data class ChatResponse(
        val response: String? = null,
        @SerializedName("audio_base64")
        val audioBase64: String? = null,
        val intents: List<IntentDto>? = null,
        val language: String? = null,
        val transcription: String? = null
    )

    data class IntentDto(
        val type: String,
        val value: String,
        val timing: String? = null,
        val duration: Long? = null
    )

    override suspend fun sendText(text: String, languageInstruction: String?): LlmResponse? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Sending text to LLM: ${text.take(50)}...")

                val request = ChatRequest(
                    message = text,
                    model = modelId,
                    languageInstruction = languageInstruction
                )

                val jsonBody = gson.toJson(request)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$serverUrl$ENDPOINT_CHAT")
                    .apply {
                        apiKey?.let { addHeader("Authorization", "Bearer $it") }
                    }
                    .post(requestBody)
                    .build()

                val response = client.newCall(httpRequest).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                    parseResponse(chatResponse)
                } else {
                    Log.e(TAG, "LLM error: ${response.code} - ${response.body?.string()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM request failed", e)
                null
            }
        }
    }

    override suspend fun sendAudio(audioData: ByteArray, languageInstruction: String?): LlmResponse? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Sending audio to LLM (${audioData.size} bytes)")

                val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)

                val request = AudioChatRequest(
                    audioBase64 = audioBase64,
                    model = modelId,
                    languageInstruction = languageInstruction,
                    returnAudio = false // We'll use TTS for audio generation
                )

                val jsonBody = gson.toJson(request)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$serverUrl$ENDPOINT_AUDIO")
                    .apply {
                        apiKey?.let { addHeader("Authorization", "Bearer $it") }
                    }
                    .post(requestBody)
                    .build()

                val response = client.newCall(httpRequest).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                    parseResponse(chatResponse)
                } else {
                    Log.e(TAG, "LLM audio error: ${response.code} - ${response.body?.string()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM audio request failed", e)
                null
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
                Log.e(TAG, "LLM health check failed", e)
                false
            }
        }
    }

    private fun parseResponse(chatResponse: ChatResponse): LlmResponse {
        val audioBytes = chatResponse.audioBase64?.let {
            Base64.decode(it, Base64.DEFAULT)
        }

        val intentTags = chatResponse.intents?.map { dto ->
            IntentTag(
                type = dto.type,
                value = dto.value,
                timing = dto.timing,
                duration = dto.duration
            )
        } ?: emptyList()

        return LlmResponse(
            responseText = chatResponse.response,
            responseAudio = audioBytes,
            intentTags = intentTags,
            detectedLanguage = chatResponse.language,
            transcription = chatResponse.transcription
        )
    }
}

/**
 * Mock LLM Client for testing
 * Supports English and German responses based on language instruction
 */
class MockLlmClient : LlmClient {
    companion object {
        private const val TAG = "MockLlmClient"
    }

    // English responses
    private val responsesEn = listOf(
        LlmResponse(
            responseText = "Hello! I'm your robot assistant. How can I help you today?",
            intentTags = listOf(
                IntentTag("emotion", "happy", "start"),
                IntentTag("action", "wave", "end")
            ),
            detectedLanguage = "en"
        ),
        LlmResponse(
            responseText = "That's a great question! Let me think about it.",
            intentTags = listOf(
                IntentTag("emotion", "thinking", "start")
            ),
            detectedLanguage = "en"
        ),
        LlmResponse(
            responseText = "I understand. Is there anything else I can help you with?",
            intentTags = listOf(
                IntentTag("emotion", "normal", "start")
            ),
            detectedLanguage = "en"
        )
    )

    // German responses
    private val responsesDe = listOf(
        LlmResponse(
            responseText = "Hallo! Ich bin dein Roboter-Assistent. Wie kann ich dir heute helfen?",
            intentTags = listOf(
                IntentTag("emotion", "happy", "start"),
                IntentTag("action", "wave", "end")
            ),
            detectedLanguage = "de"
        ),
        LlmResponse(
            responseText = "Das ist eine gute Frage! Lass mich darüber nachdenken.",
            intentTags = listOf(
                IntentTag("emotion", "thinking", "start")
            ),
            detectedLanguage = "de"
        ),
        LlmResponse(
            responseText = "Ich verstehe. Kann ich dir sonst noch helfen?",
            intentTags = listOf(
                IntentTag("emotion", "normal", "start")
            ),
            detectedLanguage = "de"
        )
    )

    private var responseIndexEn = 0
    private var responseIndexDe = 0

    override suspend fun sendText(text: String, languageInstruction: String?): LlmResponse {
        Log.d(TAG, "Mock LLM received text: $text, language: $languageInstruction")
        return getNextResponse(languageInstruction)
    }

    override suspend fun sendAudio(audioData: ByteArray, languageInstruction: String?): LlmResponse {
        Log.d(TAG, "Mock LLM received audio: ${audioData.size} bytes, language: $languageInstruction")
        val isGerman = languageInstruction?.contains("Deutsch", ignoreCase = true) == true
        return getNextResponse(languageInstruction).copy(
            transcription = if (isGerman) "Mock-Transkription der Audioeingabe" else "Mock transcription of audio input"
        )
    }

    override suspend fun isAvailable(): Boolean = true

    private fun getNextResponse(languageInstruction: String?): LlmResponse {
        // Check if German is requested
        val isGerman = languageInstruction?.contains("Deutsch", ignoreCase = true) == true

        return if (isGerman) {
            val response = responsesDe[responseIndexDe % responsesDe.size]
            responseIndexDe++
            response
        } else {
            val response = responsesEn[responseIndexEn % responsesEn.size]
            responseIndexEn++
            response
        }
    }
}
