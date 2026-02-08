package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Structured LLM Response (mandatory JSON format) for CONTINUOUS CONVERSATION
 *
 * MANDATORY FIELDS for session management:
 * - keep_session: Whether to continue the conversation (default true)
 * - conversation_end: Whether this is the final response (default false)
 * - ask_followup: Whether to ask a follow-up question (default false)
 */
data class LLMResponse(
    val speech: String,
    val emotion: Emotion = Emotion.NEUTRAL,
    val action: String? = null,
    val expression: String? = null,
    val light: String? = null,
    val earcon: Earcon = Earcon.NONE,
    val audioData: ByteArray? = null,
    val audioFormat: String? = null,
    @SerializedName("allow_barge_in")
    val allowBargeIn: Boolean = true,

    // ═══════════════════════════════════════════════════════════════
    // SESSION MANAGEMENT FIELDS (MANDATORY FOR CONTINUOUS CONVERSATION)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Keep the session alive after this response.
     * true = continue conversation, robot will listen for follow-up
     * false = end conversation after this response
     */
    @SerializedName("keep_session")
    val keepSession: Boolean = true,

    /**
     * Whether this response ends the conversation.
     * true = user explicitly wants to stop (said goodbye, etc.)
     * false = normal response, conversation may continue
     */
    @SerializedName("conversation_end")
    val conversationEnd: Boolean = false,

    /**
     * Whether to ask a follow-up question.
     * Only set true when it helps continuation (not every turn).
     * Robot should limit follow-ups to once every 2-3 turns.
     */
    @SerializedName("ask_followup")
    val askFollowup: Boolean = false,

    val timing: ResponseTiming = ResponseTiming(),
    val transcription: String? = null,
    val language: String? = null
) {
    enum class Emotion(val chinese: String, val expressionId: String?) {
        HAPPY("开心", "emo_007"),
        NEUTRAL("中性", "normal_1"),
        COMFORT("安慰", "emo_006"),
        SORRY("抱歉", "emo_014"),
        SURPRISED("惊讶", "codemao8"),
        THINKING("思考", "emo_010"),
        EXCITED("兴奋", "emo_008");

        companion object {
            fun fromString(value: String?): Emotion {
                if (value == null) return NEUTRAL
                return values().find {
                    it.name.equals(value, ignoreCase = true) ||
                    it.chinese == value
                } ?: NEUTRAL
            }
        }
    }

    enum class Earcon {
        NONE,
        LISTENING,
        THINKING,
        CONFIRM;

        companion object {
            fun fromString(value: String?): Earcon {
                if (value == null) return NONE
                return values().find { it.name.equals(value, ignoreCase = true) } ?: NONE
            }
        }
    }

    data class ResponseTiming(
        @SerializedName("pre_roll_ms")
        val preRollMs: Long = 150,
        @SerializedName("post_roll_ms")
        val postRollMs: Long = 200
    )

    /**
     * Check if this response indicates the conversation should end
     */
    fun shouldEndConversation(): Boolean {
        return conversationEnd || !keepSession
    }
}

/**
 * LLM Client Interface
 */
interface ILLMClient {
    suspend fun sendText(text: String, language: String): LLMResponse?
    suspend fun sendAudio(audioData: ByteArray, language: String): LLMResponse?
    suspend fun sendTextWithContext(
        text: String,
        language: String,
        context: LLMConversationContext?
    ): LLMResponse?
    suspend fun isAvailable(): Boolean
}

/**
 * HTTP-based LLM Client with structured JSON response parsing
 * Supports continuous conversation with context passing
 */
class LLMClient(
    private val config: DialogueConfig
) : ILLMClient {
    companion object {
        private const val TAG = "LLMClient"
        private const val ENDPOINT_CHAT = "/v1/chat"
        private const val ENDPOINT_AUDIO_CHAT = "/v1/audio/chat"
        private const val ENDPOINT_HEALTH = "/health"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.networkConnectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.networkReadTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()

    // Request DTOs
    data class TextChatRequest(
        val message: String,
        @SerializedName("language_instruction")
        val languageInstruction: String,
        @SerializedName("system_prompt")
        val systemPrompt: String? = null,
        @SerializedName("response_format")
        val responseFormat: String = "json",
        // Conversation context for multi-turn
        @SerializedName("session_id")
        val sessionId: String? = null,
        @SerializedName("turn_index")
        val turnIndex: Int? = null,
        @SerializedName("context_summary")
        val contextSummary: String? = null,
        @SerializedName("last_user_intent")
        val lastUserIntent: String? = null,
        @SerializedName("last_robot_response")
        val lastRobotResponse: String? = null
    )

    data class AudioChatRequest(
        @SerializedName("audio_base64")
        val audioBase64: String,
        @SerializedName("language_instruction")
        val languageInstruction: String,
        @SerializedName("response_format")
        val responseFormat: String = "json",
        // Conversation context for multi-turn
        @SerializedName("session_id")
        val sessionId: String? = null,
        @SerializedName("turn_index")
        val turnIndex: Int? = null,
        @SerializedName("context_summary")
        val contextSummary: String? = null,
        @SerializedName("last_user_intent")
        val lastUserIntent: String? = null,
        @SerializedName("last_robot_response")
        val lastRobotResponse: String? = null
    )

    // Raw server response DTO
    data class ServerResponse(
        val response: String? = null,
        val speech: String? = null,
        val emotion: String? = null,
        val action: String? = null,
        val expression: String? = null,
        val light: String? = null,
        val earcon: String? = null,
        @SerializedName("audio_base64")
        val audioBase64: String? = null,
        @SerializedName("audio_format")
        val audioFormat: String? = null,
        @SerializedName("allow_barge_in")
        val allowBargeIn: Boolean? = null,
        @SerializedName("ask_followup")
        val askFollowup: Boolean? = null,
        // Session management fields
        @SerializedName("keep_session")
        val keepSession: Boolean? = null,
        @SerializedName("conversation_end")
        val conversationEnd: Boolean? = null,
        val timing: LLMResponse.ResponseTiming? = null,
        val transcription: String? = null,
        val language: String? = null,
        val intents: List<IntentDto>? = null
    )

    data class IntentDto(
        val type: String,
        val value: String,
        val timing: String? = null
    )

    override suspend fun sendText(text: String, language: String): LLMResponse? {
        return sendTextWithContext(text, language, null)
    }

    override suspend fun sendTextWithContext(
        text: String,
        language: String,
        context: LLMConversationContext?
    ): LLMResponse? {
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(config.llmTimeoutMs) {
                    Log.d(TAG, "Sending text to LLM: ${text.take(50)}...")

                    val request = TextChatRequest(
                        message = text,
                        languageInstruction = getLanguageInstruction(language),
                        systemPrompt = getSystemPrompt(language),
                        sessionId = context?.sessionId,
                        turnIndex = context?.turnIndex,
                        contextSummary = context?.summary,
                        lastUserIntent = context?.lastUserIntent,
                        lastRobotResponse = context?.recentTurns?.lastOrNull()?.content
                    )

                    val jsonBody = gson.toJson(request)
                    val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                    val httpRequest = Request.Builder()
                        .url("${config.llmServerUrl}$ENDPOINT_CHAT")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(httpRequest).execute()

                    if (response.isSuccessful) {
                        parseResponse(response.body?.string(), text)
                    } else {
                        Log.e(TAG, "LLM error: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM text request failed", e)
                null
            }
        }
    }

    override suspend fun sendAudio(audioData: ByteArray, language: String): LLMResponse? {
        return sendAudioWithContext(audioData, language, null)
    }

    suspend fun sendAudioWithContext(
        audioData: ByteArray,
        language: String,
        context: LLMConversationContext?
    ): LLMResponse? {
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(config.llmTimeoutMs) {
                    Log.d(TAG, "Sending audio to LLM (${audioData.size} bytes)")

                    val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)

                    val request = AudioChatRequest(
                        audioBase64 = audioBase64,
                        languageInstruction = getLanguageInstruction(language),
                        sessionId = context?.sessionId,
                        turnIndex = context?.turnIndex,
                        contextSummary = context?.summary,
                        lastUserIntent = context?.lastUserIntent,
                        lastRobotResponse = context?.recentTurns?.lastOrNull()?.content
                    )

                    val jsonBody = gson.toJson(request)
                    val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                    val httpRequest = Request.Builder()
                        .url("${config.llmServerUrl}$ENDPOINT_AUDIO_CHAT")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(httpRequest).execute()

                    if (response.isSuccessful) {
                        parseResponse(response.body?.string(), null)
                    } else {
                        Log.e(TAG, "LLM audio error: ${response.code}")
                        null
                    }
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
                    .url("${config.llmServerUrl}$ENDPOINT_HEALTH")
                    .get()
                    .build()

                client.newCall(request).execute().isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "LLM health check failed", e)
                false
            }
        }
    }

    private fun parseResponse(responseBody: String?, userText: String?): LLMResponse? {
        if (responseBody.isNullOrBlank()) return null

        return try {
            // Try parsing as structured response first
            val serverResponse = gson.fromJson(responseBody, ServerResponse::class.java)
            convertToLLMResponse(serverResponse, userText)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Failed to parse as structured JSON, using fallback", e)
            // Fallback: treat entire response as speech
            LLMResponse(
                speech = responseBody.trim(),
                emotion = LLMResponse.Emotion.NEUTRAL,
                keepSession = true,
                conversationEnd = false
            )
        }
    }

    private fun convertToLLMResponse(server: ServerResponse, userText: String?): LLMResponse {
        // Extract speech from 'speech' or 'response' field
        val speech = server.speech ?: server.response ?: if (server.audioBase64 != null) "" else "..."

        // Parse emotion from string or intents
        var emotion = LLMResponse.Emotion.fromString(server.emotion)
        var action = server.action
        var expression = server.expression
        var light = server.light

        // Extract from intents if present
        server.intents?.forEach { intent ->
            when (intent.type.lowercase()) {
                "emotion" -> emotion = LLMResponse.Emotion.fromString(intent.value)
                "action" -> action = intent.value
                "expression" -> expression = intent.value
                "light" -> light = intent.value
            }
        }

        // Detect conversation end from speech content if not explicitly set
        val conversationEnd = server.conversationEnd
            ?: detectConversationEndFromSpeech(speech, userText)

        // Default keep_session to true unless conversation_end is true
        val keepSession = server.keepSession ?: !conversationEnd

        val audioData = server.audioBase64?.takeIf { it.isNotBlank() }?.let { base64 ->
            try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode audio_base64", e)
                null
            }
        }

        return LLMResponse(
            speech = speech,
            emotion = emotion,
            action = action,
            expression = expression ?: emotion.expressionId,
            light = light,
            earcon = LLMResponse.Earcon.fromString(server.earcon),
            audioData = audioData,
            audioFormat = server.audioFormat,
            allowBargeIn = server.allowBargeIn ?: true,
            keepSession = keepSession,
            conversationEnd = conversationEnd,
            askFollowup = server.askFollowup ?: false,
            timing = server.timing ?: LLMResponse.ResponseTiming(),
            transcription = server.transcription,
            language = server.language
        )
    }

    /**
     * Detect if the conversation should end based on speech content
     */
    private fun detectConversationEndFromSpeech(speech: String, userText: String?): Boolean {
        val lowerSpeech = speech.lowercase()
        val lowerUser = userText?.lowercase() ?: ""

        // Check if user said a stop phrase
        val stopPhrasesEn = listOf(
            "that's all", "thats all", "goodbye", "bye", "stop",
            "end", "finish", "i'm done", "im done", "nothing else"
        )
        val stopPhrasesDe = listOf(
            "das war's", "das wars", "tschüss", "auf wiedersehen",
            "fertig", "genug", "ende", "stopp", "beenden"
        )
        val allStopPhrases = stopPhrasesEn + stopPhrasesDe

        if (allStopPhrases.any { lowerUser.contains(it) }) {
            return true
        }

        // Check if robot is saying goodbye
        val goodbyePhrases = listOf(
            "goodbye", "bye", "see you", "take care",
            "tschüss", "auf wiedersehen", "bis bald", "mach's gut"
        )
        return goodbyePhrases.any { lowerSpeech.contains(it) }
    }

    private fun getLanguageInstruction(language: String): String {
        return when {
            language.startsWith("de") -> "Antworte auf Deutsch. Halte die Antwort kurz und natürlich."
            else -> "Answer in English. Keep your response short and conversational."
        }
    }

    private fun getSystemPrompt(language: String): String {
        val languageNote = if (language.startsWith("de")) {
            "Respond in German."
        } else {
            "Respond in English."
        }

        return """
            You are Alpha Mini, a friendly robot assistant. $languageNote

            CRITICAL: Return valid JSON ONLY with this structure:
            {
              "speech": "What you want to say",
              "emotion": "开心|中性|安慰|抱歉|惊讶",
              "action": "optional_action_id",
              "expression": "optional_expression_id",
              "light": "optional_light_mode",
              "earcon": "none|listening|thinking|confirm",
              "allow_barge_in": true,
              "keep_session": true,
              "ask_followup": false,
              "conversation_end": false,
              "timing": {
                "pre_roll_ms": 150,
                "post_roll_ms": 200
              }
            }

            SESSION MANAGEMENT RULES:
            - keep_session = true: Continue conversation (default)
            - keep_session = false: End after this response
            - conversation_end = true: ONLY when user clearly wants to stop
            - ask_followup = true: Only when needed (max once per 2-3 turns)

            CONVERSATION RULES:
            - Keep speech SHORT (1-2 sentences max)
            - Use natural conversational tone
            - Match emotion to content
            - Detect stop phrases: "that's all", "goodbye", "stop", etc.
            - When user says stop phrase, set conversation_end=true
        """.trimIndent()
    }
}

/**
 * Generate LLM system prompt for CONTINUOUS CONVERSATION
 */
object LLMSystemPrompt {
    fun generate(language: String): String {
        val languageNote = when {
            language.startsWith("de") -> "Antworte auf Deutsch."
            else -> "Respond in English."
        }

        return """
            |You are Alpha Mini, a friendly and helpful robot assistant.
            |$languageNote
            |
            |CRITICAL: You MUST respond with valid JSON ONLY. No other text.
            |
            |Required JSON format for CONTINUOUS CONVERSATION:
            |{
            |  "speech": "What you want to say to the user",
            |  "emotion": "开心|中性|安慰|抱歉|惊讶",
            |  "action": "wave|nod|dance|bow|clap|think|idle",
            |  "expression": "emo_007|normal_1|emo_014|codemao8|emo_010",
            |  "earcon": "none|listening|thinking|confirm",
            |  "allow_barge_in": true,
            |  "keep_session": true,
            |  "ask_followup": false,
            |  "conversation_end": false,
            |  "timing": {
            |    "pre_roll_ms": 150,
            |    "post_roll_ms": 200
            |  }
            |}
            |
            |═══════════════════════════════════════════════════════════════
            |SESSION MANAGEMENT (MANDATORY)
            |═══════════════════════════════════════════════════════════════
            |
            |keep_session:
            |  - true (default): Robot will listen for follow-up after speaking
            |  - false: Conversation ends after this response
            |
            |conversation_end:
            |  - true: ONLY when user explicitly wants to stop
            |  - false (default): Normal response
            |
            |ask_followup:
            |  - true: Ask "Anything else?" or similar
            |  - false (default): Don't ask follow-up
            |  - Limit to once every 2-3 turns!
            |
            |═══════════════════════════════════════════════════════════════
            |STOP PHRASE DETECTION
            |═══════════════════════════════════════════════════════════════
            |
            |When user says ANY of these, set conversation_end=true:
            |English: "that's all", "goodbye", "bye", "stop", "end", "finish"
            |German: "das war's", "tschüss", "auf wiedersehen", "fertig", "ende"
            |
            |═══════════════════════════════════════════════════════════════
            |EMOTION & ACTION MAPPING
            |═══════════════════════════════════════════════════════════════
            |
            |Emotion mapping:
            |  开心 = happy, positive → expression emo_007
            |  中性 = neutral, informational → expression normal_1
            |  安慰 = comforting, sympathetic → expression emo_006
            |  抱歉 = sorry, apologetic → expression emo_014
            |  惊讶 = surprised, curious → expression codemao8
            |
            |Action mapping:
            |  wave = friendly greeting
            |  nod = acknowledgment
            |  dance = celebration/fun
            |  bow = respect/thanks
            |  think = processing/considering
            |  idle = no movement needed
            |
            |═══════════════════════════════════════════════════════════════
            |RULES
            |═══════════════════════════════════════════════════════════════
            |
            |1. Keep speech SHORT - 1-2 sentences maximum
            |2. Use natural, conversational language
            |3. Match emotion to your message content
            |4. Only set ask_followup=true if you need clarification
            |5. If user is unclear, ask ONE short question with max 2 options
            |6. Never use technical jargon
            |7. Be helpful but concise
            |8. ALWAYS detect and respond to stop phrases appropriately
        """.trimMargin()
    }
}
