package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Audio frame with metadata
 */
data class AudioFrame(
    val data: ByteArray,
    val timestampMs: Long,
    val rmsEnergy: Float,
    val maxAmplitude: Short,
    val isSpeech: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return data.contentEquals(other.data) && timestampMs == other.timestampMs
    }

    override fun hashCode(): Int = data.contentHashCode()
}

/**
 * VAD (Voice Activity Detection) result
 */
sealed class VADResult {
    object Silence : VADResult()
    object SpeechStarted : VADResult()
    object SpeechContinuing : VADResult()
    data class SpeechEnded(val audioData: ByteArray) : VADResult()
    object Timeout : VADResult()
}

/**
 * Streaming Audio Recorder with integrated VAD
 *
 * Features:
 * - Streams PCM frames via Flow (20ms chunks)
 * - Energy-based VAD with noise floor calibration
 * - Android audio effects (AEC, AGC, NS) when available
 * - Clipping detection
 * - 4-mic array support
 * - Buffer pooling to minimize allocations
 */
class StreamingAudioRecorder(
    private val config: DialogueConfig
) {
    companion object {
        private const val TAG = "StreamingAudioRecorder"
        private const val CLIPPING_THRESHOLD: Short = 30000 // Near max 32767
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    @Volatile
    private var isRecording = false

    // VAD state
    private var noiseFloor: Float = 0.01f
    private var hasCalibrated = false  // Track if we've done initial calibration
    private var speechFrameCount = 0
    private var silenceFrameCount = 0
    private var hasSpeechStarted = false
    private var recordingStartTime = 0L

    // Buffer pool for reuse (minimize allocations in hot path)
    private val bufferPool = ArrayDeque<ShortArray>(10)

    // Accumulated audio data
    private val accumulatedAudio = mutableListOf<ByteArray>()

    /**
     * Start recording and stream audio frames with VAD
     * Returns a Flow of VADResult indicating speech activity
     */
    fun recordWithVAD(): Flow<VADResult> = channelFlow {
        initializeAudioRecord()

        audioRecord?.let { recorder ->
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }

            enableAudioEffects(recorder.audioSessionId)

            recorder.startRecording()
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            resetVADState()

            Log.d(TAG, "Recording started (sampleRate=${config.sampleRate}, " +
                    "frameSize=${config.frameSizeMs}ms, 4mic=${config.use4MicArray})")

            // Calibrate noise floor
            calibrateNoiseFloor(recorder)

            val buffer = acquireBuffer()
            var clippingWarningCount = 0

            try {
                while (isRecording && isActive) {
                    val readResult = recorder.read(buffer, 0, config.frameSizeSamples)

                    if (readResult > 0) {
                        val frame = processFrame(buffer, readResult)

                        // Check for clipping
                        if (frame.maxAmplitude > CLIPPING_THRESHOLD) {
                            clippingWarningCount++
                            if (clippingWarningCount % 10 == 1) {
                                Log.w(TAG, "Audio clipping detected! amplitude=${frame.maxAmplitude}")
                            }
                        }

                        // Accumulate audio
                        accumulatedAudio.add(frame.data.copyOf())

                        // VAD decision
                        val vadResult = processVAD(frame)
                        send(vadResult)

                        // Check for speech end or timeout
                        when (vadResult) {
                            is VADResult.SpeechEnded, is VADResult.Timeout -> {
                                isRecording = false
                                break
                            }
                            else -> {}
                        }

                        // Check max recording timeout
                        if (System.currentTimeMillis() - recordingStartTime > config.maxRecordingMs) {
                            Log.d(TAG, "Max recording timeout reached")
                            send(VADResult.Timeout)
                            isRecording = false
                            break
                        }
                    } else if (readResult < 0) {
                        Log.e(TAG, "AudioRecord read error: $readResult")
                        isRecording = false
                        break
                    }
                }
            } finally {
                releaseBuffer(buffer)
                stopRecordingInternal()
            }
        }
    }.buffer(Channel.BUFFERED).flowOn(Dispatchers.IO)

    /**
     * Get raw audio stream without VAD processing
     */
    fun recordRaw(): Flow<AudioFrame> = channelFlow {
        initializeAudioRecord()

        audioRecord?.let { recorder ->
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }

            enableAudioEffects(recorder.audioSessionId)
            recorder.startRecording()
            isRecording = true
            recordingStartTime = System.currentTimeMillis()

            val buffer = acquireBuffer()

            try {
                while (isRecording && isActive) {
                    val readResult = recorder.read(buffer, 0, config.frameSizeSamples)

                    if (readResult > 0) {
                        val frame = processFrame(buffer, readResult)
                        send(frame)
                    } else if (readResult < 0) {
                        Log.e(TAG, "AudioRecord read error: $readResult")
                        break
                    }
                }
            } finally {
                releaseBuffer(buffer)
                stopRecordingInternal()
            }
        }
    }.buffer(Channel.BUFFERED).flowOn(Dispatchers.IO)

    /**
     * Stop recording
     */
    fun stopRecording() {
        isRecording = false
    }

    /**
     * Get accumulated audio data
     */
    fun getAccumulatedAudio(): ByteArray {
        val totalSize = accumulatedAudio.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        accumulatedAudio.forEach { chunk ->
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size
        }
        return result
    }

    /**
     * Clear accumulated audio
     */
    fun clearAccumulatedAudio() {
        accumulatedAudio.clear()
    }

    private fun initializeAudioRecord() {
        val channelConfig = config.getEffectiveChannelConfig()

        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            channelConfig,
            config.encoding
        )

        // Use larger buffer to prevent overruns (4x minimum)
        val bufferSize = maxOf(minBufferSize * 4, config.frameSizeBytes * 8)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                channelConfig,
                config.encoding,
                bufferSize
            )

            Log.d(TAG, "AudioRecord created: bufferSize=$bufferSize, minBuffer=$minBufferSize")
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission not granted", e)
            throw e
        }
    }

    private fun enableAudioEffects(audioSessionId: Int) {
        // Acoustic Echo Canceler
        if (AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "AcousticEchoCanceler enabled")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable AcousticEchoCanceler", e)
            }
        }

        // Noise Suppressor
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "NoiseSuppressor enabled")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable NoiseSuppressor", e)
            }
        }

        // Automatic Gain Control
        if (AutomaticGainControl.isAvailable()) {
            try {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "AutomaticGainControl enabled")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable AutomaticGainControl", e)
            }
        }
    }

    private fun calibrateNoiseFloor(recorder: AudioRecord) {
        // Skip calibration if already done in this session
        // This prevents user's early speech from being treated as noise
        if (hasCalibrated) {
            Log.d(TAG, "Skipping noise calibration (already calibrated, noiseFloor=$noiseFloor)")
            return
        }

        Log.d(TAG, "Calibrating noise floor (first time)...")
        // Use shorter calibration time (100ms instead of 300ms) to minimize delay
        val calibrationMs = 100L
        val calibrationFrames = (calibrationMs / config.frameSizeMs).toInt()
        val buffer = ShortArray(config.frameSizeSamples)
        var totalEnergy = 0f
        var frameCount = 0

        repeat(calibrationFrames) {
            val read = recorder.read(buffer, 0, config.frameSizeSamples)
            if (read > 0) {
                totalEnergy += calculateRMS(buffer, read)
                frameCount++
            }
        }

        if (frameCount > 0) {
            noiseFloor = (totalEnergy / frameCount) * 1.5f // 50% margin
            noiseFloor = noiseFloor.coerceIn(0.001f, 0.03f) // Lower max to be more sensitive
            hasCalibrated = true
            Log.d(TAG, "Noise floor calibrated: $noiseFloor")
        }
    }

    /**
     * Reset calibration (call when starting a new session)
     */
    fun resetCalibration() {
        hasCalibrated = false
        noiseFloor = 0.01f
        Log.d(TAG, "Noise calibration reset")
    }

    private fun processFrame(buffer: ShortArray, sampleCount: Int): AudioFrame {
        // Convert to bytes
        val byteBuffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            byteBuffer.putShort(buffer[i])
        }

        val rms = calculateRMS(buffer, sampleCount)
        val maxAmp = buffer.take(sampleCount).maxOfOrNull { abs(it.toInt()).toShort() } ?: 0

        // If 4-mic mode, downmix to mono for processing
        val processedData = if (config.use4MicArray) {
            downmixToMono(byteBuffer.array(), 4)
        } else {
            byteBuffer.array()
        }

        return AudioFrame(
            data = processedData,
            timestampMs = System.currentTimeMillis(),
            rmsEnergy = rms,
            maxAmplitude = maxAmp,
            isSpeech = rms > noiseFloor * 2
        )
    }

    private fun processVAD(frame: AudioFrame): VADResult {
        // More sensitive speech detection:
        // - Use lower threshold (0.015 instead of config value which might be 0.02)
        // - Or detect if energy is 2x noise floor (instead of 3x)
        val effectiveThreshold = minOf(config.vadEnergyThreshold, 0.015f)
        val isSpeech = frame.rmsEnergy > effectiveThreshold ||
                frame.rmsEnergy > noiseFloor * 2  // Changed from 3x to 2x for sensitivity

        if (isSpeech) {
            silenceFrameCount = 0
            speechFrameCount++

            // Detect speech faster: require only 2 frames (40ms) instead of 3 (60ms)
            val minFrames = minOf(config.vadSpeechMinFrames, 2)
            if (!hasSpeechStarted && speechFrameCount >= minFrames) {
                hasSpeechStarted = true
                Log.d(TAG, "Speech started (energy=${frame.rmsEnergy}, threshold=$effectiveThreshold, noiseFloor=$noiseFloor)")
                return VADResult.SpeechStarted
            }
            return if (hasSpeechStarted) VADResult.SpeechContinuing else VADResult.Silence
        } else {
            if (hasSpeechStarted) {
                silenceFrameCount++
                val silenceDuration = silenceFrameCount * config.frameSizeMs

                if (silenceDuration >= config.vadSilenceTimeoutMs + config.vadHangoverMs) {
                    Log.d(TAG, "Speech ended after ${silenceDuration}ms silence")
                    return VADResult.SpeechEnded(getAccumulatedAudio())
                }
                return VADResult.SpeechContinuing
            }
            speechFrameCount = 0
            return VADResult.Silence
        }
    }

    private fun calculateRMS(samples: ShortArray, count: Int): Float {
        if (count == 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val normalized = samples[i].toDouble() / Short.MAX_VALUE
            sum += normalized * normalized
        }
        return sqrt(sum / count).toFloat()
    }

    private fun downmixToMono(multiChannelData: ByteArray, channelCount: Int): ByteArray {
        val samplesPerChannel = multiChannelData.size / (2 * channelCount)
        val monoData = ByteArray(samplesPerChannel * 2)
        val inputBuffer = ByteBuffer.wrap(multiChannelData).order(ByteOrder.LITTLE_ENDIAN)
        val outputBuffer = ByteBuffer.wrap(monoData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until samplesPerChannel) {
            var sum = 0
            for (ch in 0 until channelCount) {
                sum += inputBuffer.getShort((i * channelCount + ch) * 2).toInt()
            }
            outputBuffer.putShort(i * 2, (sum / channelCount).toShort())
        }

        return monoData
    }

    private fun resetVADState() {
        speechFrameCount = 0
        silenceFrameCount = 0
        hasSpeechStarted = false
        accumulatedAudio.clear()
    }

    private fun stopRecordingInternal() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            echoCanceler?.release()
            echoCanceler = null

            noiseSuppressor?.release()
            noiseSuppressor = null

            gainControl?.release()
            gainControl = null

            Log.d(TAG, "Recording stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    // Buffer pool management
    private fun acquireBuffer(): ShortArray {
        return bufferPool.removeLastOrNull() ?: ShortArray(config.frameSizeSamples)
    }

    private fun releaseBuffer(buffer: ShortArray) {
        if (bufferPool.size < 10) {
            bufferPool.addLast(buffer)
        }
    }

    fun release() {
        stopRecordingInternal()
        bufferPool.clear()
    }
}
