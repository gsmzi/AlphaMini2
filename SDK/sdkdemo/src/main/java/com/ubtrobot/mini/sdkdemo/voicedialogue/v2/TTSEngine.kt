package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import kotlinx.coroutines.flow.Flow

/**
 * Language for TTS synthesis.
 */
enum class TTSLang(val code: String, val bcp47: String) {
    EN("en", "en-US"),
    DE("de", "de-DE")
}

/**
 * TTS Engine interface for synthesizing text to raw PCM audio.
 *
 * All implementations produce 16kHz, 16-bit, mono PCM data that can be
 * played directly through [PcmAudioPlayer].
 */
interface TTSEngine {
    val isReady: Boolean

    suspend fun initialize()

    /**
     * Stream PCM chunks for the given text (16kHz, 16-bit, mono).
     * Each emitted ByteArray is a chunk of PCM data.
     */
    fun synthesizeToPcm(text: String, lang: TTSLang): Flow<ByteArray>

    /**
     * Synthesize full text to a single PCM buffer.
     * Returns null on failure.
     */
    suspend fun synthesizeAll(text: String, lang: TTSLang): ByteArray?

    /**
     * Stop any in-progress synthesis.
     */
    fun stop()

    /**
     * Release all resources.
     */
    fun release()
}
