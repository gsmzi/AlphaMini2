package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

/**
 * Voice Dialogue Configuration for CONTINUOUS MULTI-TURN CONVERSATION
 *
 * All timing and threshold values are tuned for low-latency conversational interaction
 * with seamless multi-turn dialogue support.
 */
data class DialogueConfig(
    // ═══════════════════════════════════════════════════════════════
    // AUDIO INPUT
    // ═══════════════════════════════════════════════════════════════
    val sampleRate: Int = 16000,
    val encoding: Int = android.media.AudioFormat.ENCODING_PCM_16BIT,
    val channelConfig: Int = android.media.AudioFormat.CHANNEL_IN_MONO,
    val use4MicArray: Boolean = false,
    val micArrayChannelConfig: Int = 60, // Wukong 4-mic array

    // Frame size for streaming (20ms = 320 samples at 16kHz)
    val frameSizeMs: Int = 20,
    val frameSizeSamples: Int = sampleRate * frameSizeMs / 1000,
    val frameSizeBytes: Int = frameSizeSamples * 2, // 16-bit = 2 bytes

    // ═══════════════════════════════════════════════════════════════
    // VAD (Voice Activity Detection)
    // ═══════════════════════════════════════════════════════════════
    val vadEnergyThreshold: Float = 0.02f,        // RMS threshold for speech
    val vadSilenceThreshold: Float = 0.005f,      // RMS threshold for silence
    val vadSpeechMinFrames: Int = 3,              // Min frames to confirm speech
    val vadSilenceTimeoutMs: Long = 800,          // Silence before endpoint (fast cutoff)
    val vadHangoverMs: Long = 300,                // Extra time after silence detected
    val vadNoiseCalibrationMs: Long = 300,        // Initial noise floor calibration

    // ═══════════════════════════════════════════════════════════════
    // CONTINUOUS CONVERSATION - SILENCE THRESHOLDS (CONFIGURABLE)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Short pause threshold (ms) - Keep listening
     * User is thinking, don't interrupt
     */
    val silenceShortPauseMs: Long = 2000,

    /**
     * Long silence threshold (ms) - Trigger gentle prompt
     * "I'm here if you need me" or "Anything else?"
     */
    val silenceLongMs: Long = 5000,

    /**
     * Very long silence threshold (ms) - End session gracefully
     * Say goodbye and return to idle
     */
    val silenceVeryLongMs: Long = 10000,

    /**
     * Maximum session duration (ms) - Auto-end after this time
     * Prevents indefinitely open sessions
     */
    val maxSessionDurationMs: Long = 300000,  // 5 minutes

    /**
     * Follow-up frequency - Ask follow-up every N turns (minimum)
     * Prevents asking "anything else?" every turn
     */
    val followUpMinTurns: Int = 2,

    /**
     * Follow-up frequency - Maximum turns between follow-ups
     */
    val followUpMaxTurns: Int = 4,

    // ═══════════════════════════════════════════════════════════════
    // TIMEOUTS
    // ═══════════════════════════════════════════════════════════════
    val maxRecordingMs: Long = 15000,             // Max recording duration per turn
    val llmTimeoutMs: Long = 10000,               // LLM response timeout
    val ttsTimeoutMs: Long = 8000,                // TTS generation timeout
    val networkConnectTimeoutMs: Long = 5000,     // Network connect timeout
    val networkReadTimeoutMs: Long = 15000,       // Network read timeout

    // ═══════════════════════════════════════════════════════════════
    // LATENCY TARGETS
    // ═══════════════════════════════════════════════════════════════
    val targetResponseLatencyMs: Long = 500,      // Target: 300-600ms
    val fillerThresholdMs: Long = 800,            // Show filler if LLM takes longer
    val earconDurationMs: Long = 200,             // Earcon sound duration

    // ═══════════════════════════════════════════════════════════════
    // BEHAVIOR SYNC
    // ═══════════════════════════════════════════════════════════════
    val defaultPreRollMs: Long = 150,             // Start behavior before audio
    val defaultPostRollMs: Long = 200,            // Keep behavior after audio ends
    val bargeInEnabled: Boolean = true,           // Allow user interruption

    // ═══════════════════════════════════════════════════════════════
    // CONTINUOUS CONVERSATION MODE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Enable continuous conversation mode (multi-turn without re-wakeup)
     */
    val continuousConversationEnabled: Boolean = true,

    /**
     * Delay before starting follow-up listening (ms)
     * Natural pause after robot finishes speaking
     */
    val followUpListeningDelayMs: Long = 300,

    /**
     * Enable automatic follow-up prompts on silence
     */
    val autoPromptOnSilence: Boolean = true,

    // ═══════════════════════════════════════════════════════════════
    // SERVERS
    // ═══════════════════════════════════════════════════════════════
    val llmServerUrl: String = "http://127.0.0.1:8080",
    val ttsServerUrl: String = "http://127.0.0.1:5000",

    // ═══════════════════════════════════════════════════════════════
    // LANGUAGE
    // ═══════════════════════════════════════════════════════════════
    val language: Language = Language.EN,
    val enableAutoLanguageDetection: Boolean = false
) {
    enum class Language(val code: String, val ttsCode: String) {
        EN("en", "en-US"),
        DE("de", "de-DE")
    }

    fun getEffectiveChannelConfig(): Int {
        return if (use4MicArray) micArrayChannelConfig else channelConfig
    }

    companion object {
        fun forEnglish() = DialogueConfig(language = Language.EN)
        fun forGerman() = DialogueConfig(language = Language.DE)

        fun forLowLatency() = DialogueConfig(
            vadSilenceTimeoutMs = 600,
            fillerThresholdMs = 600,
            targetResponseLatencyMs = 400,
            silenceShortPauseMs = 1500,
            silenceLongMs = 4000,
            silenceVeryLongMs = 8000
        )

        /**
         * Configuration optimized for continuous conversation
         */
        fun forContinuousConversation() = DialogueConfig(
            continuousConversationEnabled = true,
            silenceShortPauseMs = 2000,
            silenceLongMs = 5000,
            silenceVeryLongMs = 10000,
            followUpMinTurns = 2,
            followUpMaxTurns = 4,
            autoPromptOnSilence = true
        )

        /**
         * Configuration for quick Q&A mode (single turn)
         */
        fun forSingleTurn() = DialogueConfig(
            continuousConversationEnabled = false,
            vadSilenceTimeoutMs = 600,
            silenceVeryLongMs = 3000
        )
    }

    /**
     * Silence prompt messages by language
     */
    fun getSilencePrompt(): String {
        return when (language) {
            Language.DE -> "Ich bin hier, wenn du mich brauchst."
            Language.EN -> "I'm here if you need me."
        }
    }

    /**
     * Follow-up prompts by language
     */
    fun getFollowUpPrompts(): List<String> {
        return when (language) {
            Language.DE -> listOf(
                "Noch etwas?",
                "Kann ich dir sonst noch helfen?",
                "Möchtest du fortfahren?"
            )
            Language.EN -> listOf(
                "Anything else?",
                "Is there something else I can help with?",
                "Do you want me to continue?"
            )
        }
    }

    /**
     * Goodbye messages by language
     */
    fun getGoodbyeMessages(): List<String> {
        return when (language) {
            Language.DE -> listOf(
                "Tschüss! Bis bald!",
                "Auf Wiedersehen!",
                "Mach's gut!"
            )
            Language.EN -> listOf(
                "Goodbye! Talk to you later!",
                "See you later!",
                "Take care!"
            )
        }
    }

    /**
     * Stop phrases that should end the conversation
     */
    fun getStopPhrases(): List<String> {
        return when (language) {
            Language.DE -> listOf(
                "das war's",
                "das wars",
                "das ist alles",
                "geh schlafen",
                "schlaf gut",
                "tschüss",
                "auf wiedersehen",
                "danke das ist alles",
                "fertig",
                "genug",
                "ende",
                "stopp",
                "beenden"
            )
            Language.EN -> listOf(
                "that's all",
                "thats all",
                "that is all",
                "go to sleep",
                "goodbye",
                "bye bye",
                "thank you that's all",
                "i'm done",
                "im done",
                "stop",
                "end",
                "finish",
                "that's enough",
                "no more",
                "nothing else"
            )
        }
    }
}
