package com.ubtrobot.mini.sdkdemo.voicedialogue.wakeup

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*

/**
 * Wake-up detector using voice wake word detection
 *
 * This is a stub implementation that provides the interface for plugging in
 * wake word engines like Porcupine, Snowboy, Sensory, etc.
 *
 * To use a real wake word engine:
 * 1. Implement WakeWordEngine interface with your chosen library
 * 2. Pass it to the constructor
 */
class WakeWordWakeupDetector(
    private val wakeWordEngine: WakeWordEngine? = null,
    private val sampleRate: Int = 16000
) : WakeUpDetector {
    companion object {
        private const val TAG = "WakeWordWakeupDetector"
        private const val FRAME_SIZE = 512 // Samples per frame
    }

    private var callback: WakeUpCallback? = null
    private var isActive = false
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun setWakeUpCallback(callback: WakeUpCallback) {
        this.callback = callback
    }

    override fun start() {
        if (isActive) {
            Log.d(TAG, "Already active")
            return
        }

        if (wakeWordEngine == null) {
            Log.w(TAG, "No wake word engine configured - voice wake-up disabled")
            return
        }

        if (!wakeWordEngine.initialize()) {
            Log.e(TAG, "Failed to initialize wake word engine")
            return
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize.coerceAtLeast(FRAME_SIZE * 2)
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                return
            }

            isActive = true
            startListening()
            Log.d(TAG, "Wake word detector started, listening for: ${wakeWordEngine.getWakeWord()}")

        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word detector", e)
        }
    }

    private fun startListening() {
        recordingJob = scope.launch {
            val buffer = ShortArray(FRAME_SIZE)
            val byteBuffer = ByteArray(FRAME_SIZE * 2)

            try {
                audioRecord?.startRecording()

                while (isActive && isActive()) {
                    val readResult = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1

                    if (readResult > 0) {
                        // Convert shorts to bytes
                        for (i in 0 until readResult) {
                            byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                        }

                        // Feed to wake word engine
                        val detected = wakeWordEngine?.acceptPcmFrame(
                            byteBuffer.copyOf(readResult * 2)
                        ) ?: false

                        if (detected) {
                            Log.d(TAG, "Wake word detected!")
                            withContext(Dispatchers.Main) {
                                callback?.invoke(WakeUpType.VOICE)
                            }
                            // Pause after detection to avoid multiple triggers
                            delay(1000)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Wake word listening cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake word detection loop", e)
            }
        }
    }

    override fun stop() {
        if (!isActive) return

        isActive = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Wake word detector stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word detector", e)
        }
    }

    override fun isActive(): Boolean = isActive

    override fun release() {
        stop()
        wakeWordEngine?.release()
        scope.cancel()
        callback = null
    }
}

/**
 * Stub wake word engine for testing
 * Replace with real implementation (Porcupine, etc.)
 */
class StubWakeWordEngine(private val wakeWord: String = "Hey Robot") : WakeWordEngine {
    companion object {
        private const val TAG = "StubWakeWordEngine"
    }

    private var initialized = false

    override fun initialize(): Boolean {
        Log.d(TAG, "Stub wake word engine initialized (wake word: $wakeWord)")
        Log.w(TAG, "This is a stub - integrate a real wake word engine for production")
        initialized = true
        return true
    }

    override fun acceptPcmFrame(pcmData: ByteArray): Boolean {
        // Stub implementation - never detects wake word
        // Real implementation would analyze PCM data
        return false
    }

    override fun getWakeWord(): String = wakeWord

    override fun release() {
        initialized = false
        Log.d(TAG, "Stub wake word engine released")
    }
}
