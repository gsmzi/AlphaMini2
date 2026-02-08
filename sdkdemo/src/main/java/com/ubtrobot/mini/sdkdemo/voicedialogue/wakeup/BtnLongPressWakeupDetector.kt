package com.ubtrobot.mini.sdkdemo.voicedialogue.wakeup

import android.util.Log
import com.ubtrobot.masterevent.protos.SysMasterEvent
import com.ubtrobot.mini.sysevent.SysEventApi
import com.ubtrobot.mini.sysevent.event.ChestEvent
import com.ubtrobot.mini.sysevent.event.base.KeyEvent
import com.ubtrobot.mini.sysevent.receiver.KeyEventReceiver

/**
 * Wake-up detector using chest button long press
 * Uses SysEventApi to subscribe to chest button events
 */
class BtnLongPressWakeupDetector : WakeUpDetector {
    companion object {
        private const val TAG = "BtnLongPressWakeup"
    }

    private var callback: WakeUpCallback? = null
    private var isActive = false
    private var receiver: ChestKeyEventReceiver? = null

    override fun setWakeUpCallback(callback: WakeUpCallback) {
        this.callback = callback
    }

    override fun start() {
        if (isActive) {
            Log.d(TAG, "Already active")
            return
        }

        try {
            receiver = ChestKeyEventReceiver { type ->
                callback?.invoke(type)
            }

            SysEventApi.get().subscribe(
                ChestEvent.newInstance().setPriority(SysMasterEvent.Priority.NORMAL),
                receiver
            )

            isActive = true
            Log.d(TAG, "Button wake-up detector started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start button wake-up detector", e)
        }
    }

    override fun stop() {
        if (!isActive) return

        try {
            receiver?.let {
                SysEventApi.get().unsubscribe(it)
            }
            receiver = null
            isActive = false
            Log.d(TAG, "Button wake-up detector stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop button wake-up detector", e)
        }
    }

    override fun isActive(): Boolean = isActive

    override fun release() {
        stop()
        callback = null
    }

    /**
     * Internal receiver for chest key events
     */
    private class ChestKeyEventReceiver(
        private val onWakeUp: (WakeUpType) -> Unit
    ) : KeyEventReceiver() {

        override fun onSingleClick(event: KeyEvent?): Boolean {
            Log.d(TAG, "Chest single click - ignored for wake-up")
            return false // Don't consume, let other handlers process
        }

        override fun onDoubleClick(event: KeyEvent?): Boolean {
            Log.d(TAG, "Chest double click - ignored for wake-up")
            return false
        }

        override fun onLongClick(event: KeyEvent?): Boolean {
            Log.d(TAG, "Chest long click - WAKE UP triggered!")
            onWakeUp(WakeUpType.BUTTON)
            return true // Consume the event
        }
    }
}
