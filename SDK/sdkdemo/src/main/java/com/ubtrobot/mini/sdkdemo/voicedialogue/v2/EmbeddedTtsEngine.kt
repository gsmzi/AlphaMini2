package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.content.Context
import android.util.Log

/**
 * Embedded offline TTS engine wrapper (e.g., eSpeak NG via JNI).
 *
 * Requires:
 * - libespeak-ng.so in src/main/jniLibs/<abi>/
 * - assets/espeak-ng-data/ (copied to filesDir on first run)
 *
 * Synthesizes 16kHz, 16-bit, mono PCM.
 */
class EmbeddedTtsEngine(private val context: Context) {
    companion object {
        private const val TAG = "EmbeddedTtsEngine"
    }

    @Volatile
    var isReady: Boolean = false
        private set

    private var currentLanguage = DialogueConfig.Language.EN

    fun initialize(language: DialogueConfig.Language = DialogueConfig.Language.EN): Boolean {
        currentLanguage = language

        val dataDir = EmbeddedTtsInstaller.ensureInstalled(context)
        if (dataDir == null || !dataDir.exists()) {
            Log.e(TAG, "TTS data directory missing")
            isReady = false
            return false
        }

        try {
            System.loadLibrary("embedded_tts")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load embedded_tts native library", e)
            isReady = false
            return false
        }

        val ok = nativeInit(dataDir.absolutePath)
        if (!ok) {
            Log.e(TAG, "Native TTS init failed")
            isReady = false
            return false
        }

        setLanguage(language)
        Log.d(TAG, "Embedded TTS supports voices: en, de")
        isReady = true
        Log.d(TAG, "Embedded TTS ready (lang=$currentLanguage)")
        return true
    }

    fun setLanguage(language: DialogueConfig.Language) {
        currentLanguage = language
        if (!isReady) return
        val langCode = if (language == DialogueConfig.Language.DE) "de" else "en"
        val ok = nativeSetVoice(langCode)
        Log.d(TAG, "Embedded TTS setLanguage($langCode) => $ok")
    }

    fun synthesize(text: String): ByteArray? {
        if (!isReady) {
            Log.w(TAG, "Embedded TTS not ready")
            return null
        }
        if (text.isBlank()) return ByteArray(0)
        val langCode = if (currentLanguage == DialogueConfig.Language.DE) "de" else "en"
        return nativeSynthesize(text, langCode)
    }

    fun release() {
        isReady = false
        nativeShutdown()
    }

    private external fun nativeInit(dataPath: String): Boolean
    private external fun nativeSetVoice(lang: String): Boolean
    private external fun nativeSynthesize(text: String, lang: String): ByteArray?
    private external fun nativeShutdown()
}
