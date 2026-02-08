package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Callbacks for PCM playback lifecycle.
 */
interface PcmPlaybackListener {
    fun onPlaybackStart()
    fun onPlaybackEnd()
    fun onPlaybackStopped() // barge-in / forced stop
}

/**
 * AudioTrack MODE_STREAM wrapper for playing raw PCM audio.
 *
 * Accepts either a [Flow] of PCM chunks or a single [ByteArray].
 * Produces low-latency output through AudioTrack with USAGE_ASSISTANT.
 * Supports instant stop for barge-in (no drain).
 *
 * Audio format: 16kHz, 16-bit, mono (matching [TTSEngine] output).
 */
class PcmAudioPlayer(private val sampleRate: Int = 16000) {

    companion object {
        private const val TAG = "PcmAudioPlayer"
    }

    private var audioTrack: AudioTrack? = null
    private var listener: PcmPlaybackListener? = null
    private val playing = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private var playbackJob: Job? = null

    private val bufferSize: Int by lazy {
        AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2
    }

    fun setListener(listener: PcmPlaybackListener?) {
        this.listener = listener
    }

    /**
     * Play a stream of PCM chunks. Suspends until all chunks are written
     * and playback completes, or until [stop] is called.
     *
     * @return true if playback completed normally, false if stopped/error
     */
    suspend fun playPcmStream(pcmFlow: Flow<ByteArray>): Boolean {
        return withContext(Dispatchers.IO) {
            stopCurrent()
            stopRequested.set(false)

            try {
                val track = createAudioTrack()
                audioTrack = track
                track.play()
                playing.set(true)

                withContext(Dispatchers.Main) {
                    listener?.onPlaybackStart()
                }

                pcmFlow.collect { chunk ->
                    if (stopRequested.get()) {
                        throw CancellationException("Stop requested")
                    }
                    writeChunk(track, chunk)
                }

                if (!stopRequested.get()) {
                    // Let buffer drain before signalling end
                    delay(100)
                }

                releaseTrack()

                if (!stopRequested.get()) {
                    withContext(Dispatchers.Main) {
                        listener?.onPlaybackEnd()
                    }
                    true
                } else {
                    false
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "PCM stream playback cancelled")
                releaseTrack()
                false
            } catch (e: Exception) {
                Log.e(TAG, "PCM stream playback error", e)
                releaseTrack()
                false
            } finally {
                playing.set(false)
            }
        }
    }

    /**
     * Play a single PCM byte array. Suspends until complete or stopped.
     *
     * @return true if playback completed normally, false if stopped/error
     */
    suspend fun playPcmBytes(pcmData: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            stopCurrent()
            stopRequested.set(false)

            if (pcmData.isEmpty()) return@withContext true

            try {
                val track = createAudioTrack()
                audioTrack = track
                track.play()
                playing.set(true)

                withContext(Dispatchers.Main) {
                    listener?.onPlaybackStart()
                }

                Log.d(TAG, "Playing ${pcmData.size} bytes PCM")

                var offset = 0
                val chunkSize = bufferSize / 2
                while (offset < pcmData.size && !stopRequested.get()) {
                    val writeSize = minOf(chunkSize, pcmData.size - offset)
                    val written = track.write(pcmData, offset, writeSize)
                    if (written > 0) {
                        offset += written
                    } else if (written < 0) {
                        Log.e(TAG, "AudioTrack write error: $written")
                        break
                    }
                    yield()
                }

                if (!stopRequested.get()) {
                    delay(100) // drain
                }

                releaseTrack()

                if (!stopRequested.get()) {
                    withContext(Dispatchers.Main) {
                        listener?.onPlaybackEnd()
                    }
                    true
                } else {
                    false
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "PCM bytes playback cancelled")
                releaseTrack()
                false
            } catch (e: Exception) {
                Log.e(TAG, "PCM bytes playback error", e)
                releaseTrack()
                false
            } finally {
                playing.set(false)
            }
        }
    }

    /**
     * Stop playback immediately (barge-in). Does not drain the buffer.
     */
    fun stop() {
        stopRequested.set(true)
        playbackJob?.cancel()
        playbackJob = null

        releaseTrack()

        if (playing.getAndSet(false)) {
            listener?.onPlaybackStopped()
        }
    }

    fun isPlaying(): Boolean = playing.get()

    fun release() {
        stop()
    }

    // ─── internals ───────────────────────────────────────────────

    private fun createAudioTrack(): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun writeChunk(track: AudioTrack, chunk: ByteArray) {
        var offset = 0
        while (offset < chunk.size && !stopRequested.get()) {
            val writeSize = minOf(chunk.size - offset, bufferSize / 2)
            val written = track.write(chunk, offset, writeSize)
            if (written > 0) {
                offset += written
            } else if (written < 0) {
                Log.e(TAG, "AudioTrack write error: $written")
                break
            }
        }
    }

    private fun stopCurrent() {
        releaseTrack()
        playbackJob?.cancel()
        playbackJob = null
        playing.set(false)
    }

    private fun releaseTrack() {
        audioTrack?.let { track ->
            try {
                track.stop()
            } catch (_: Exception) { }
            try {
                track.release()
            } catch (_: Exception) { }
        }
        audioTrack = null
    }
}
