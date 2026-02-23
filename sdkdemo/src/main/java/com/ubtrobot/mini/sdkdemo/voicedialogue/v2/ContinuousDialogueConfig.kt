package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

/**
 * Configuration for Continuous Multi-Turn Dialogue
 */
data class ContinuousDialogueConfig(
    // ═══════════════════════════════════════════════════════════════
    // AUDIO SETTINGS
    // ═══════════════════════════════════════════════════════════════
    val sampleRate: Int = 16000,
    val frameSizeMs: Int = 20,
    val channelConfig: Int = 16,  // MONO=16, 4-MIC=60

    // Wakeup settings
    val enableVoiceWakeup: Boolean = true,
    val enableButtonWakeup: Boolean = true,
    val wakeWords: List<String> = listOf("hello wukong", "hi wukong", "wukong"),

    // ═══════════════════════════════════════════════════════════════
    // VAD SETTINGS (Voice Activity Detection)
    // ═══════════════════════════════════════════════════════════════
    val vadEnergyThreshold: Float = 0.02f,
    val vadSpeechMinMs: Long = 100,

    // ═══════════════════════════════════════════════════════════════
    // SILENCE THRESHOLDS (Multi-Level)
    // ═══════════════════════════════════════════════════════════════

    /** Short pause during speech - keep listening (user thinking) */
    val silenceShortPauseMs: Long = 800,

    /** End of utterance - process speech, stay in session */
    val silenceEndOfUtteranceMs: Long = 1500,

    /** Long silence after robot speaks - prompt user gently */
    val silenceLongMs: Long = 5000,

    /** Very long silence - end session gracefully */
    val silenceVeryLongMs: Long = 10000,

    /** Max wait time in ListeningForFollowUp before prompting */
    val followUpWaitMs: Long = 3000,

    // ═══════════════════════════════════════════════════════════════
    // SESSION SETTINGS
    // ═══════════════════════════════════════════════════════════════

    /** Maximum session duration before forced end */
    val maxSessionDurationMs: Long = 5 * 60 * 1000,  // 5 minutes

    /** Maximum turns per session */
    val maxTurnsPerSession: Int = 20,

    /** Turns between follow-up questions (min 2) */
    val followUpFrequencyTurns: Int = 3,

    // ═══════════════════════════════════════════════════════════════
    // BARGE-IN SETTINGS
    // ═══════════════════════════════════════════════════════════════
    val bargeInEnabled: Boolean = true,
    val bargeInEnergyThreshold: Float = 0.03f,
    val bargeInMinDurationMs: Long = 150,

    // ═══════════════════════════════════════════════════════════════
    // BEHAVIOR SYNC SETTINGS
    // ═══════════════════════════════════════════════════════════════

    /** Start expression this many ms BEFORE speech */
    val behaviorPreRollMs: Long = 200,

    /** Keep expression this many ms AFTER speech ends */
    val behaviorPostRollMs: Long = 300,

    // ═══════════════════════════════════════════════════════════════
    // LOCAL PROCESSING (embedded TTS + LLM — no external servers)
    // ═══════════════════════════════════════════════════════════════
    val useLocalProcessing: Boolean = true,

    // ═══════════════════════════════════════════════════════════════
    // SERVER SETTINGS (only used when useLocalProcessing = false)
    // ═══════════════════════════════════════════════════════════════
    val llmServerUrl: String = "http://127.0.0.1:8080",
    val ttsServerUrl: String = "http://127.0.0.1:5000",
    val llmTimeoutMs: Long = 10000,
    val ttsTimeoutMs: Long = 15000,

    // ═══════════════════════════════════════════════════════════════
    // OPENAI (Whisper STT + GPT streaming responses)
    // ═══════════════════════════════════════════════════════════════
    val openAiApiKey: String = "",

    /** Use Whisper API as primary STT (Vosk is offline fallback). Requires openAiApiKey. */
    val useWhisper: Boolean = true,

    // ═══════════════════════════════════════════════════════════════
    // LANGUAGE
    // ═══════════════════════════════════════════════════════════════
    val language: DialogueConfig.Language = DialogueConfig.Language.EN
) {
    companion object {
        /** Stop phrases that end the conversation (English) */
        val STOP_PHRASES_EN = listOf(
            "goodbye", "bye bye", "that's all", "go to sleep",
            "i'm done", "thank you bye", "end conversation", "quit",
            "that is all", "no more", "nothing else"
        )

        /** Stop phrases that end the conversation (German) */
        val STOP_PHRASES_DE = listOf(
            "tschüss", "tschuss", "auf wiedersehen", "das war's",
            "ich bin fertig", "danke tschüss", "beenden",
            "nichts mehr", "das reicht"
        )

        /** Follow-up prompts (English) */
        val FOLLOWUP_PROMPTS_EN = listOf(
            "Anything else?",
            "What else can I help with?",
            "Is there something else?"
        )

        /** Follow-up prompts (German) */
        val FOLLOWUP_PROMPTS_DE = listOf(
            "Noch etwas?",
            "Kann ich noch helfen?",
            "Gibt es noch etwas?"
        )

        /** Silence prompts - gentle reminder (English) */
        val SILENCE_PROMPTS_EN = listOf(
            "I'm still here if you need me.",
            "Take your time.",
            "I'm listening."
        )

        /** Silence prompts - gentle reminder (German) */
        val SILENCE_PROMPTS_DE = listOf(
            "Ich bin noch da.",
            "Lass dir Zeit.",
            "Ich höre zu."
        )

        /** Session end phrases (English) */
        val SESSION_END_EN = listOf(
            "Goodbye! It was nice talking with you.",
            "See you later!",
            "Bye for now!"
        )

        /** Session end phrases (German) */
        val SESSION_END_DE = listOf(
            "Auf Wiedersehen! Es war schön mit dir zu sprechen.",
            "Bis später!",
            "Tschüss!"
        )
    }

    fun getStopPhrases(lang: DialogueConfig.Language = language): List<String> = when (lang) {
        DialogueConfig.Language.EN -> STOP_PHRASES_EN
        DialogueConfig.Language.DE -> STOP_PHRASES_DE
    }

    fun getFollowUpPrompts(lang: DialogueConfig.Language = language): List<String> = when (lang) {
        DialogueConfig.Language.EN -> FOLLOWUP_PROMPTS_EN
        DialogueConfig.Language.DE -> FOLLOWUP_PROMPTS_DE
    }

    fun getSilencePrompts(lang: DialogueConfig.Language = language): List<String> = when (lang) {
        DialogueConfig.Language.EN -> SILENCE_PROMPTS_EN
        DialogueConfig.Language.DE -> SILENCE_PROMPTS_DE
    }

    fun getSessionEndPhrases(lang: DialogueConfig.Language = language): List<String> = when (lang) {
        DialogueConfig.Language.EN -> SESSION_END_EN
        DialogueConfig.Language.DE -> SESSION_END_DE
    }
}
