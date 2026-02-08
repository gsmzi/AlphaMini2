package com.ubtrobot.mini.sdkdemo.voicedialogue

import java.util.Locale

/**
 * Language mode for voice dialogue
 */
enum class LanguageMode {
    EN,    // English only
    DE,    // German only
    AUTO   // Auto-detect from ASR or model response
}

/**
 * Configuration for Voice Dialogue system
 */
data class VoiceDialogueConfig(
    /**
     * Language mode: EN, DE, or AUTO
     */
    val languageMode: LanguageMode = LanguageMode.EN,

    /**
     * Base URL for the TTS server (Edge TTS)
     */
    val ttsServerUrl: String = "http://127.0.0.1:5000",

    /**
     * Base URL for the LLM service
     */
    val llmServerUrl: String = "http://127.0.0.1:8080",

    /**
     * Whether to use direct audio upload instead of ASR->text->upload
     */
    val useDirectAudioUpload: Boolean = false,

    /**
     * Audio sample rate for recording (default 16000 Hz)
     */
    val sampleRate: Int = 16000,

    /**
     * Whether to use 4-mic array configuration (channelConfig=60)
     */
    val use4MicArray: Boolean = false,

    /**
     * Recording timeout in milliseconds (max recording duration)
     * Reduced to 10 seconds for faster testing
     */
    val recordingTimeoutMs: Long = 10000,

    /**
     * Silence detection timeout in milliseconds (VAD)
     * Reduced to 1000ms for faster response
     */
    val silenceTimeoutMs: Long = 1000,

    /**
     * Enable voice wake-up detection
     */
    val enableVoiceWakeUp: Boolean = false,

    /**
     * Enable button (chest) wake-up
     */
    val enableButtonWakeUp: Boolean = true,

    /**
     * Enable robot expressions during dialogue
     */
    val enableExpressions: Boolean = true,

    /**
     * Enable robot actions/motions during dialogue
     */
    val enableActions: Boolean = true,

    /**
     * Enable LED lights during dialogue
     */
    val enableLights: Boolean = true,

    /**
     * Enable continuous conversation mode (auto-listen after each response)
     */
    val enableContinuousConversation: Boolean = true
) {
    companion object {
        /**
         * Default English configuration
         */
        fun defaultEnglish() = VoiceDialogueConfig(
            languageMode = LanguageMode.EN
        )

        /**
         * Default German configuration
         */
        fun defaultGerman() = VoiceDialogueConfig(
            languageMode = LanguageMode.DE
        )

        /**
         * Auto-detect language configuration
         */
        fun autoDetect() = VoiceDialogueConfig(
            languageMode = LanguageMode.AUTO
        )
    }

    /**
     * Get the TTS language code based on language mode
     */
    fun getTtsLanguageCode(detectedLanguage: String? = null): String {
        return when (languageMode) {
            LanguageMode.EN -> "en-US"
            LanguageMode.DE -> "de-DE"
            LanguageMode.AUTO -> {
                when {
                    detectedLanguage?.lowercase()?.startsWith("de") == true -> "de-DE"
                    detectedLanguage?.lowercase()?.startsWith("en") == true -> "en-US"
                    else -> "en-US" // Default to English
                }
            }
        }
    }

    /**
     * Get the Locale for TTS based on language mode
     */
    fun getLocale(detectedLanguage: String? = null): Locale {
        return when (languageMode) {
            LanguageMode.EN -> Locale.US
            LanguageMode.DE -> Locale.GERMANY
            LanguageMode.AUTO -> {
                when {
                    detectedLanguage?.lowercase()?.startsWith("de") == true -> Locale.GERMANY
                    detectedLanguage?.lowercase()?.startsWith("en") == true -> Locale.US
                    else -> Locale.US
                }
            }
        }
    }

    /**
     * Get the LLM system prompt language instruction
     */
    fun getLlmLanguageInstruction(detectedLanguage: String? = null): String {
        return when (languageMode) {
            LanguageMode.EN -> "Answer in English."
            LanguageMode.DE -> "Antworte auf Deutsch."
            LanguageMode.AUTO -> {
                when {
                    detectedLanguage?.lowercase()?.startsWith("de") == true -> "Antworte auf Deutsch."
                    else -> "Answer in English."
                }
            }
        }
    }
}

/**
 * Localized strings for voice dialogue feedback
 */
object VoiceDialogueStrings {
    data class LocalizedString(
        val en: String,
        val de: String
    ) {
        fun get(languageMode: LanguageMode, detectedLanguage: String? = null): String {
            return when (languageMode) {
                LanguageMode.EN -> en
                LanguageMode.DE -> de
                LanguageMode.AUTO -> {
                    when {
                        detectedLanguage?.lowercase()?.startsWith("de") == true -> de
                        else -> en
                    }
                }
            }
        }
    }

    val LISTENING = LocalizedString(
        en = "Listening...",
        de = "Ich hoere zu..."
    )

    val PROCESSING = LocalizedString(
        en = "One moment...",
        de = "Einen Moment..."
    )

    val DID_NOT_CATCH = LocalizedString(
        en = "I didn't catch that.",
        de = "Das habe ich nicht verstanden."
    )

    val ERROR_OCCURRED = LocalizedString(
        en = "Sorry, an error occurred.",
        de = "Entschuldigung, ein Fehler ist aufgetreten."
    )

    val READY = LocalizedString(
        en = "Ready",
        de = "Bereit"
    )

    val WAKING_UP = LocalizedString(
        en = "I'm here!",
        de = "Ich bin da!"
    )

    val GOODBYE = LocalizedString(
        en = "Goodbye!",
        de = "Auf Wiedersehen!"
    )
}
