package com.ubtrobot.mini.sdkdemo.voicedialogue.wakeup

/**
 * Type of wake-up trigger
 */
enum class WakeUpType {
    VOICE,   // Voice wake word detected
    BUTTON   // Physical button (chest) pressed
}

/**
 * Callback for wake-up events
 */
typealias WakeUpCallback = (WakeUpType) -> Unit

/**
 * Interface for wake-up detection
 */
interface WakeUpDetector {
    /**
     * Set callback for wake-up events
     */
    fun setWakeUpCallback(callback: WakeUpCallback)

    /**
     * Start listening for wake-up events
     */
    fun start()

    /**
     * Stop listening for wake-up events
     */
    fun stop()

    /**
     * Check if detector is currently active
     */
    fun isActive(): Boolean

    /**
     * Release resources
     */
    fun release()
}

/**
 * Interface for wake word detection engine
 * Implementations can plug in different wake word engines (Porcupine, Snowboy, etc.)
 */
interface WakeWordEngine {
    /**
     * Initialize the wake word engine
     * @return true if initialization successful
     */
    fun initialize(): Boolean

    /**
     * Process PCM audio frame
     * @param pcmData PCM audio data (16-bit, mono, 16kHz)
     * @return true if wake word detected
     */
    fun acceptPcmFrame(pcmData: ByteArray): Boolean

    /**
     * Get the wake word being detected
     */
    fun getWakeWord(): String

    /**
     * Release resources
     */
    fun release()
}
