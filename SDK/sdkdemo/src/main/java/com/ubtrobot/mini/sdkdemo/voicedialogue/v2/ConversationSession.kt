package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Log
import java.util.UUID

/**
 * Conversation Session Manager
 *
 * Tracks multi-turn conversation state, context, and history.
 * Enables coherent dialogue across multiple exchanges.
 */
data class ConversationTurn(
    val turnIndex: Int,
    val userInput: String,
    val robotResponse: String,
    val emotion: String?,
    val action: String?,
    val timestamp: Long = System.currentTimeMillis()
)

data class ConversationContext(
    val summary: String = "",
    val lastUserIntent: String? = null,
    val lastRobotAction: String? = null,
    val topics: MutableList<String> = mutableListOf(),
    val userPreferences: MutableMap<String, String> = mutableMapOf()
)

class ConversationSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val language: DialogueConfig.Language = DialogueConfig.Language.EN,
    val startTime: Long = System.currentTimeMillis()
) {
    companion object {
        private const val TAG = "ConversationSession"

        // Keep last N turns verbatim
        private const val VERBATIM_TURN_LIMIT = 2

        // Max turns before forcing summary compression
        private const val MAX_HISTORY_TURNS = 10
    }

    private val _turns = mutableListOf<ConversationTurn>()
    val turns: List<ConversationTurn> get() = _turns.toList()

    var turnIndex: Int = 0
        private set

    var context: ConversationContext = ConversationContext()
        private set

    var isActive: Boolean = true
        private set

    var lastActivityTime: Long = System.currentTimeMillis()
        private set

    // Track follow-up questions to avoid asking too frequently
    private var turnsSinceLastFollowUp: Int = 0

    /**
     * Add a completed turn to the session
     */
    fun addTurn(
        userInput: String,
        robotResponse: String,
        emotion: String? = null,
        action: String? = null
    ) {
        val turn = ConversationTurn(
            turnIndex = turnIndex,
            userInput = userInput,
            robotResponse = robotResponse,
            emotion = emotion,
            action = action
        )

        _turns.add(turn)
        turnIndex++
        lastActivityTime = System.currentTimeMillis()
        turnsSinceLastFollowUp++

        // Update context
        context = context.copy(
            lastUserIntent = extractIntent(userInput),
            lastRobotAction = action
        )

        // Compress history if needed
        if (_turns.size > MAX_HISTORY_TURNS) {
            compressHistory()
        }

        Log.d(TAG, "Turn $turnIndex added. Total turns: ${_turns.size}")
    }

    /**
     * Check if we should ask a follow-up question
     * (Max once every 2-3 turns)
     */
    fun shouldAskFollowUp(): Boolean {
        return turnsSinceLastFollowUp >= 2
    }

    /**
     * Mark that we asked a follow-up
     */
    fun markFollowUpAsked() {
        turnsSinceLastFollowUp = 0
    }

    /**
     * Get the session duration in milliseconds
     */
    fun getDurationMs(): Long {
        return System.currentTimeMillis() - startTime
    }

    /**
     * Get time since last activity in milliseconds
     */
    fun getIdleTimeMs(): Long {
        return System.currentTimeMillis() - lastActivityTime
    }

    /**
     * Build context for LLM request
     * Uses rolling summary + recent verbatim turns
     */
    fun buildLLMContext(): LLMConversationContext {
        val recentTurns = _turns.takeLast(VERBATIM_TURN_LIMIT)

        return LLMConversationContext(
            sessionId = sessionId,
            turnIndex = turnIndex,
            summary = context.summary,
            recentTurns = recentTurns.map { turn ->
                LLMTurnContext(
                    role = "user",
                    content = turn.userInput
                ) to LLMTurnContext(
                    role = "assistant",
                    content = turn.robotResponse
                )
            }.flatMap { listOf(it.first, it.second) },
            lastUserIntent = context.lastUserIntent,
            lastRobotAction = context.lastRobotAction
        )
    }

    /**
     * End the session
     */
    fun endSession(reason: String) {
        isActive = false
        Log.d(TAG, "Session ended: $reason. Total turns: $turnIndex, Duration: ${getDurationMs()}ms")
    }

    /**
     * Update activity timestamp
     */
    fun touch() {
        lastActivityTime = System.currentTimeMillis()
    }

    /**
     * Compress older turns into a summary
     */
    private fun compressHistory() {
        if (_turns.size <= VERBATIM_TURN_LIMIT) return

        val turnsToSummarize = _turns.dropLast(VERBATIM_TURN_LIMIT)

        // Build summary from older turns
        val summaryParts = mutableListOf<String>()

        if (context.summary.isNotEmpty()) {
            summaryParts.add(context.summary)
        }

        turnsToSummarize.forEach { turn ->
            summaryParts.add("User asked about: ${extractIntent(turn.userInput)}")
        }

        // Keep summary concise (max 3 sentences)
        val newSummary = summaryParts.takeLast(3).joinToString(". ")

        context = context.copy(summary = newSummary)

        // Remove summarized turns
        repeat(turnsToSummarize.size) {
            _turns.removeAt(0)
        }

        Log.d(TAG, "History compressed. Summary: $newSummary")
    }

    /**
     * Extract simple intent from user input
     */
    private fun extractIntent(input: String): String {
        val lowerInput = input.lowercase()

        return when {
            lowerInput.contains("weather") || lowerInput.contains("wetter") -> "weather"
            lowerInput.contains("time") || lowerInput.contains("uhrzeit") || lowerInput.contains("zeit") -> "time"
            lowerInput.contains("name") || lowerInput.contains("heißt") -> "name"
            lowerInput.contains("dance") || lowerInput.contains("tanz") -> "dance"
            lowerInput.contains("joke") || lowerInput.contains("witz") -> "joke"
            lowerInput.contains("help") || lowerInput.contains("hilfe") -> "help"
            lowerInput.contains("bye") || lowerInput.contains("tschüss") || lowerInput.contains("goodbye") -> "farewell"
            else -> "general"
        }
    }
}

/**
 * LLM-ready conversation context
 */
data class LLMConversationContext(
    val sessionId: String,
    val turnIndex: Int,
    val summary: String,
    val recentTurns: List<LLMTurnContext>,
    val lastUserIntent: String?,
    val lastRobotAction: String?
)

data class LLMTurnContext(
    val role: String,
    val content: String
)

/**
 * Session Manager - handles session lifecycle
 */
object ConversationSessionManager {
    private const val TAG = "SessionManager"

    private var currentSession: ConversationSession? = null

    /**
     * Start a new session or return existing active session
     */
    fun getOrCreateSession(language: DialogueConfig.Language): ConversationSession {
        val existing = currentSession

        return if (existing != null && existing.isActive) {
            Log.d(TAG, "Returning existing session: ${existing.sessionId}")
            existing
        } else {
            val newSession = ConversationSession(language = language)
            currentSession = newSession
            Log.d(TAG, "Created new session: ${newSession.sessionId}")
            newSession
        }
    }

    /**
     * Get current session if exists and active
     */
    fun getCurrentSession(): ConversationSession? {
        return currentSession?.takeIf { it.isActive }
    }

    /**
     * End current session
     */
    fun endCurrentSession(reason: String) {
        currentSession?.endSession(reason)
        currentSession = null
    }

    /**
     * Check if there's an active session
     */
    fun hasActiveSession(): Boolean {
        return currentSession?.isActive == true
    }
}
