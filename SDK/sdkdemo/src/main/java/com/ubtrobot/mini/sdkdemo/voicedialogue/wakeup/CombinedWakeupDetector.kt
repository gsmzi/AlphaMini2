package com.ubtrobot.mini.sdkdemo.voicedialogue.wakeup

import android.util.Log

/**
 * Combined wake-up detector that supports both button and voice wake-up
 */
class CombinedWakeupDetector(
    private val enableButton: Boolean = true,
    private val enableVoice: Boolean = false,
    private val wakeWordEngine: WakeWordEngine? = null
) : WakeUpDetector {
    companion object {
        private const val TAG = "CombinedWakeupDetector"
    }

    private var callback: WakeUpCallback? = null
    private var buttonDetector: BtnLongPressWakeupDetector? = null
    private var voiceDetector: WakeWordWakeupDetector? = null
    private var isActive = false

    init {
        if (enableButton) {
            buttonDetector = BtnLongPressWakeupDetector()
        }
        if (enableVoice && wakeWordEngine != null) {
            voiceDetector = WakeWordWakeupDetector(wakeWordEngine)
        }
    }

    override fun setWakeUpCallback(callback: WakeUpCallback) {
        this.callback = callback

        // Forward callback to both detectors
        buttonDetector?.setWakeUpCallback { type ->
            Log.d(TAG, "Button wake-up triggered")
            callback(type)
        }

        voiceDetector?.setWakeUpCallback { type ->
            Log.d(TAG, "Voice wake-up triggered")
            callback(type)
        }
    }

    override fun start() {
        if (isActive) {
            Log.d(TAG, "Already active")
            return
        }

        buttonDetector?.start()
        voiceDetector?.start()
        isActive = true

        Log.d(TAG, "Combined wake-up detector started (button=$enableButton, voice=$enableVoice)")
    }

    override fun stop() {
        if (!isActive) return

        buttonDetector?.stop()
        voiceDetector?.stop()
        isActive = false

        Log.d(TAG, "Combined wake-up detector stopped")
    }

    override fun isActive(): Boolean = isActive

    override fun release() {
        stop()
        buttonDetector?.release()
        voiceDetector?.release()
        buttonDetector = null
        voiceDetector = null
        callback = null
    }
}
