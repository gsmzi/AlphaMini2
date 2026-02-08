package com.ubtrobot.mini.sdkdemo.voicedialogue.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * Audio recorder for voice dialogue with Flow-based PCM streaming
 *
 * Supports:
 * - Standard mono recording (16kHz, 16-bit PCM)
 * - 4-mic array configuration (channelConfig=60)
 * - Voice Activity Detection (VAD) for automatic stop
 */
class DialogueAudioRecorder(
    private val sampleRate: Int = 16000,
    private val use4MicArray: Boolean = false,
    private val silenceTimeoutMs: Long = 1200,  // Reduced for faster response (was 2000)
    private val silenceThreshold: Short = 600   // Lower threshold for better speech detection
) {
    companion object {
        private const val TAG = "DialogueAudioRecorder"
        private const val FRAME_SIZE_MS = 20 // 20ms per frame
        private const val CHANNEL_CONFIG_4MIC = 60 // WuKong 4-mic array config
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val frameSize = sampleRate * FRAME_SIZE_MS / 1000 // samples per frame

    /**
     * Start recording and emit PCM frames as Flow
     * Recording automatically stops on silence detection or manual stop
     */
    fun record(): Flow<ByteArray> = callbackFlow {
        val channelConfig = if (use4MicArray) {
            CHANNEL_CONFIG_4MIC
        } else {
            AudioFormat.CHANNEL_IN_MONO
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameSize * 2 * 4) // At least 4 frames

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "Recording started (sampleRate=$sampleRate, 4mic=$use4MicArray)")

            val buffer = ShortArray(frameSize)
            var silentFrameCount = 0
            val maxSilentFrames = (silenceTimeoutMs / FRAME_SIZE_MS).toInt()
            var hasReceivedSpeech = false

            while (isRecording && isActive) {
                val readResult = audioRecord?.read(buffer, 0, frameSize) ?: -1

                if (readResult > 0) {
                    // Convert to bytes
                    val byteBuffer = ByteArray(readResult * 2)
                    for (i in 0 until readResult) {
                        byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                    }

                    // Emit frame
                    trySend(byteBuffer)

                    // Voice Activity Detection
                    val maxAmplitude = buffer.take(readResult).maxOfOrNull { abs(it.toInt()) } ?: 0

                    if (maxAmplitude > silenceThreshold) {
                        if (!hasReceivedSpeech) {
                            Log.d(TAG, "Speech detected! amplitude=$maxAmplitude, threshold=$silenceThreshold")
                        }
                        hasReceivedSpeech = true
                        silentFrameCount = 0
                    } else if (hasReceivedSpeech) {
                        silentFrameCount++
                        if (silentFrameCount % 25 == 0) {  // Log every 500ms
                            Log.d(TAG, "Silence frames: $silentFrameCount/$maxSilentFrames, amplitude=$maxAmplitude")
                        }
                        if (silentFrameCount >= maxSilentFrames) {
                            Log.d(TAG, "Silence detected after ${silentFrameCount * FRAME_SIZE_MS}ms, stopping recording")
                            isRecording = false  // Stop the loop
                            break
                        }
                    }
                } else if (readResult < 0) {
                    Log.e(TAG, "AudioRecord read error: $readResult")
                    isRecording = false
                    break
                }
            }

            Log.d(TAG, "Recording loop ended, closing channel")
            // Explicitly close the channel to complete the flow
            channel.close()

        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission not granted", e)
            channel.close(e)
        } catch (e: Exception) {
            Log.e(TAG, "Recording error", e)
            channel.close(e)
        } finally {
            stopRecordingInternal()
        }

        awaitClose {
            Log.d(TAG, "awaitClose called, cleaning up")
            stopRecordingInternal()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stop recording
     */
    fun stopRecording() {
        isRecording = false
    }

    private fun stopRecordingInternal() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Recording stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Release resources
     */
    fun release() {
        stopRecordingInternal()
    }
}

/**
 * Simple Voice Activity Detector
 * Can be extended with more sophisticated algorithms (WebRTC VAD, etc.)
 */
class SimpleVAD(
    private val silenceThreshold: Short = 500,
    private val speechThreshold: Short = 1000,
    private val minSpeechFrames: Int = 5
) {
    private var speechFrameCount = 0
    private var silenceFrameCount = 0
    private var isSpeaking = false

    /**
     * Process audio frame and return VAD state
     * @return true if speech detected
     */
    fun process(samples: ShortArray): Boolean {
        val maxAmplitude = samples.maxOfOrNull { abs(it.toInt()) } ?: 0

        if (maxAmplitude > speechThreshold) {
            speechFrameCount++
            silenceFrameCount = 0
            if (speechFrameCount >= minSpeechFrames) {
                isSpeaking = true
            }
        } else if (maxAmplitude < silenceThreshold) {
            silenceFrameCount++
            speechFrameCount = 0
        }

        return isSpeaking
    }

    /**
     * Check if currently in speech
     */
    fun isSpeaking(): Boolean = isSpeaking

    /**
     * Get number of consecutive silence frames
     */
    fun getSilenceFrameCount(): Int = silenceFrameCount

    /**
     * Reset VAD state
     */
    fun reset() {
        speechFrameCount = 0
        silenceFrameCount = 0
        isSpeaking = false
    }
}
