package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.ubtrobot.masterevent.protos.SysMasterEvent
import com.ubtrobot.mini.sysevent.SysEventApi
import com.ubtrobot.mini.sysevent.event.ChestEvent
import com.ubtrobot.mini.sysevent.event.base.KeyEvent
import com.ubtrobot.mini.sysevent.receiver.KeyEventReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Configuration for voice wakeup detection.
 */
data class VoiceWakeupConfig(
    val wakeWords: List<String> = listOf("hello wukong", "hi wukong", "wukong"),
    val language: String = "en",
    val sampleRate: Int = 16000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val use4MicArray: Boolean = false,
    val micArrayChannelConfig: Int = 60,
    val cooldownMs: Long = 3000
) {
    fun effectiveChannelConfig(): Int {
        return if (use4MicArray) micArrayChannelConfig else channelConfig
    }
}

/**
 * Wakeup event data
 */
data class WakeupEvent(
    val source: WakeupSource,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f,
    val keyword: String? = null
)

/**
 * Wakeup Manager
 *
 * Handles both voice wakeup (keyword detection) and physical button wakeup.
 * Uses SysEventApi for chest button long-press detection.
 */
class WakeupManager(
    private val context: Context,
    private val voiceConfig: VoiceWakeupConfig = VoiceWakeupConfig(),
    private val enableVoiceWakeup: Boolean = false,
    private val enableButtonWakeup: Boolean = true
) {
    companion object {
        private const val TAG = "WakeupManager"
    }

    private val _wakeupEvents = MutableSharedFlow<WakeupEvent>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val wakeupEvents: SharedFlow<WakeupEvent> = _wakeupEvents.asSharedFlow()

    @Volatile
    private var isActive = false

    private var receiver: ChestKeyEventReceiver? = null
    private val wakeupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var voiceWakeupJob: Job? = null

    // Callback for legacy support
    private var wakeupCallback: ((WakeupSource) -> Unit)? = null

    /**
     * Set callback for wakeup events (legacy API)
     */
    fun setWakeupCallback(callback: (WakeupSource) -> Unit) {
        this.wakeupCallback = callback
    }

    /**
     * Start listening for wakeup events
     */
    fun start() {
        if (isActive) {
            Log.d(TAG, "Already active")
            return
        }

        isActive = true
        Log.d(TAG, "Starting wakeup detection (voice=$enableVoiceWakeup, button=$enableButtonWakeup)")

        if (enableButtonWakeup) {
            subscribeToButtonEvents()
        }

        if (enableVoiceWakeup) {
            startVoiceWakeup()
        }
    }

    /**
     * Stop listening for wakeup events
     */
    fun stop() {
        if (!isActive) return

        isActive = false
        Log.d(TAG, "Stopping wakeup detection")

        if (enableButtonWakeup) {
            unsubscribeFromButtonEvents()
        }

        if (enableVoiceWakeup) {
            stopVoiceWakeup()
        }
    }

    /**
     * Pause voice wakeup to release the mic for Google STT.
     * Button wakeup remains active.
     */
    fun pauseVoiceWakeup() {
        stopVoiceWakeup()
        Log.d(TAG, "Voice wakeup paused (mic released for STT)")
    }

    /**
     * Resume voice wakeup after Google STT is done.
     */
    fun resumeVoiceWakeup() {
        if (enableVoiceWakeup && isActive) {
            startVoiceWakeup()
            Log.d(TAG, "Voice wakeup resumed")
        }
    }

    /**
     * Manually trigger wakeup (for testing)
     */
    fun triggerManualWakeup() {
        Log.d(TAG, "Manual wakeup triggered")
        emitWakeup(WakeupEvent(
            source = WakeupSource.MANUAL_TRIGGER
        ))
    }

    /**
     * Release resources
     */
    fun release() {
        stop()
        wakeupCallback = null
        wakeupScope.cancel()
    }

    // ═══════════════════════════════════════════════════════════════
    // BUTTON WAKEUP (SysEventApi)
    // ═══════════════════════════════════════════════════════════════

    private fun subscribeToButtonEvents() {
        try {
            receiver = ChestKeyEventReceiver { source ->
                emitWakeup(WakeupEvent(source = source))
            }

            SysEventApi.get().subscribe(
                ChestEvent.newInstance().setPriority(SysMasterEvent.Priority.NORMAL),
                receiver
            )

            Log.d(TAG, "Subscribed to button events")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to button events", e)
        }
    }

    private fun unsubscribeFromButtonEvents() {
        try {
            receiver?.let {
                SysEventApi.get().unsubscribe(it)
            }
            receiver = null
            Log.d(TAG, "Unsubscribed from button events")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from button events", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VOICE WAKEUP (Placeholder)
    // ═══════════════════════════════════════════════════════════════

    private fun startVoiceWakeup() {
        if (voiceWakeupJob?.isActive == true) {
            Log.d(TAG, "Voice wakeup already running")
            return
        }

        voiceWakeupJob = wakeupScope.launch {
            Log.d(TAG, "Voice wakeup starting (wakeWords=${voiceConfig.wakeWords})")
            if (voiceConfig.wakeWords.isEmpty()) {
                Log.w(TAG, "Voice wakeup disabled: wakeWords is empty")
                return@launch
            }

            val modelPath = if (voiceConfig.language.startsWith("de")) "model-de" else "model-en-us"
            val model = loadModel(modelPath)
            if (model == null) {
                Log.e(TAG, "Voice wakeup: model not available ($modelPath)")
                return@launch
            }

            val channelConfig = voiceConfig.effectiveChannelConfig()
            val minBufferSize = AudioRecord.getMinBufferSize(
                voiceConfig.sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize * 4, voiceConfig.sampleRate)

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                voiceConfig.sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Voice wakeup: AudioRecord init failed")
                audioRecord.release()
                return@launch
            }

            val recognizer = Recognizer(model, voiceConfig.sampleRate.toFloat())
            val shortBuffer = ShortArray(bufferSize / 2)

            try {
                audioRecord.startRecording()
                Log.d(TAG, "Voice wakeup listening...")

                while (isActive && this@WakeupManager.isActive) {
                    val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                    if (read <= 0) continue
                    val monoSamples = if (voiceConfig.use4MicArray) {
                        downmixToMono(shortBuffer, read, 4)
                    } else {
                        shortBuffer.copyOfRange(0, read)
                    }
                    val monoBytes = shortsToBytes(monoSamples)
                    recognizer.acceptWaveForm(monoBytes, monoBytes.size)
                    val partial = extractPartial(recognizer.partialResult)
                    if (partial.isNotBlank() && containsWakeWord(partial)) {
                        Log.d(TAG, "Wake word detected: '$partial'")
                        emitWakeup(WakeupEvent(source = WakeupSource.VOICE_KEYWORD, keyword = partial))
                        // Cooldown to prevent repeated triggers
                        delay(voiceConfig.cooldownMs)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Voice wakeup error", e)
            } finally {
                try {
                    audioRecord.stop()
                } catch (_: Exception) {}
                audioRecord.release()
                recognizer.close()
                model.close()
                Log.d(TAG, "Voice wakeup stopped")
            }
        }
    }

    private fun stopVoiceWakeup() {
        voiceWakeupJob?.cancel()
        voiceWakeupJob = null
        Log.d(TAG, "Voice wakeup stopping")
    }

    // ═══════════════════════════════════════════════════════════════
    // EVENT EMISSION
    // ═══════════════════════════════════════════════════════════════

    private fun emitWakeup(event: WakeupEvent) {
        // Emit to Flow
        _wakeupEvents.tryEmit(event)

        // Call legacy callback
        wakeupCallback?.invoke(event.source)
    }

    /**
     * Internal receiver for chest key events
     */
    private class ChestKeyEventReceiver(
        private val onWakeUp: (WakeupSource) -> Unit
    ) : KeyEventReceiver() {

        override fun onSingleClick(event: KeyEvent?): Boolean {
            Log.d(TAG, "Chest single click - ignored for wake-up")
            return false
        }

        override fun onDoubleClick(event: KeyEvent?): Boolean {
            Log.d(TAG, "Chest double click - ignored for wake-up")
            return false
        }

        override fun onLongClick(event: KeyEvent?): Boolean {
            Log.d(TAG, "Chest long click - WAKE UP triggered!")
            onWakeUp(WakeupSource.CHEST_BUTTON)
            return true
        }
    }

    private fun loadModel(assetPath: String): Model? {
        return try {
            val outputPath = StorageService.sync(context, assetPath, assetPath)
            Model(outputPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load wakeup model: $assetPath", e)
            null
        }
    }

    private fun extractPartial(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return try {
            JSONObject(json).optString("partial", "")
        } catch (_: Exception) {
            ""
        }
    }

    private fun containsWakeWord(text: String): Boolean {
        val normalized = text.lowercase()
        return voiceConfig.wakeWords.any { word ->
            normalized.contains(word.lowercase())
        }
    }

    private fun downmixToMono(
        multiChannel: ShortArray,
        sampleCount: Int,
        channelCount: Int
    ): ShortArray {
        val frames = sampleCount / channelCount
        val mono = ShortArray(frames)
        var inIndex = 0
        for (i in 0 until frames) {
            var sum = 0
            for (ch in 0 until channelCount) {
                sum += multiChannel[inIndex++].toInt()
            }
            mono[i] = (sum / channelCount).toShort()
        }
        return mono
    }

    private fun shortsToBytes(samples: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            buffer.putShort(s)
        }
        return buffer.array()
    }
}
