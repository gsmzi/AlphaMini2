package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import com.ubtrobot.master.component.ResourcePolicy
import com.ubtrobot.mini.voice.VoiceListener
import com.ubtrobot.mini.voice.VoicePool
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio playback state
 */
enum class PlaybackState {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
    STOPPED
}

/**
 * Playback event callbacks
 */
interface PlaybackListener {
    fun onPlaybackStarted()
    fun onPlaybackComplete()
    fun onPlaybackError(error: String)
    fun onPlaybackProgress(positionMs: Long, durationMs: Long)
}

/**
 * Streaming Audio Player with barge-in support
 *
 * Features:
 * - AudioTrack for low-latency PCM playback
 * - MediaPlayer/VoicePool fallback for MP3
 * - Immediate stop for barge-in
 * - Progress tracking
 * - Buffer management
 */
class StreamingAudioPlayer(
    private val context: Context,
    private val config: DialogueConfig
) {
    companion object {
        private const val TAG = "StreamingAudioPlayer"
    }

    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    private var currentTempFile: File? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var _state = PlaybackState.IDLE
    val state: PlaybackState get() = _state

    @Volatile
    private var stopRequested = false

    private var listener: PlaybackListener? = null

    /**
     * Set playback listener
     */
    fun setListener(listener: PlaybackListener?) {
        this.listener = listener
    }

    /**
     * Play PCM audio data using AudioTrack (lowest latency)
     */
    suspend fun playPCM(pcmData: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            stop() // Stop any current playback
            stopRequested = false
            _state = PlaybackState.PREPARING

            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    config.sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(config.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
                _state = PlaybackState.PLAYING

                withContext(Dispatchers.Main) {
                    listener?.onPlaybackStarted()
                }

                Log.d(TAG, "Playing PCM audio (${pcmData.size} bytes)")

                // Write audio in chunks
                var offset = 0
                val chunkSize = bufferSize
                val totalDuration = (pcmData.size.toLong() * 1000) / (config.sampleRate * 2)

                while (offset < pcmData.size && !stopRequested) {
                    val writeSize = minOf(chunkSize, pcmData.size - offset)
                    val written = audioTrack?.write(pcmData, offset, writeSize) ?: 0

                    if (written > 0) {
                        offset += written

                        // Report progress
                        val position = (offset.toLong() * 1000) / (config.sampleRate * 2)
                        withContext(Dispatchers.Main) {
                            listener?.onPlaybackProgress(position, totalDuration)
                        }
                    } else if (written < 0) {
                        Log.e(TAG, "AudioTrack write error: $written")
                        break
                    }

                    // Yield to allow stop requests to be processed
                    yield()
                }

                if (!stopRequested) {
                    // Wait for playback to complete
                    delay(100) // Small delay to let buffer drain
                }

                stopAudioTrack()

                if (!stopRequested) {
                    _state = PlaybackState.IDLE
                    withContext(Dispatchers.Main) {
                        listener?.onPlaybackComplete()
                    }
                }

                !stopRequested
            } catch (e: CancellationException) {
                Log.d(TAG, "PCM playback cancelled")
                stopAudioTrack()
                false
            } catch (e: Exception) {
                Log.e(TAG, "PCM playback error", e)
                stopAudioTrack()
                _state = PlaybackState.IDLE
                withContext(Dispatchers.Main) {
                    listener?.onPlaybackError(e.message ?: "Playback error")
                }
                false
            }
        }
    }

    /**
     * Play MP3 audio data
     * Uses VoicePool on robot, MediaPlayer as fallback
     */
    suspend fun playMP3(mp3Data: ByteArray, useVoicePool: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            stop()
            stopRequested = false
            _state = PlaybackState.PREPARING

            try {
                // Save to temp file (detect format for correct extension)
                val ext = if (AudioFormatDetector.isWAV(mp3Data)) "wav" else "mp3"
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                currentTempFile = File(cacheDir, "speech_${System.currentTimeMillis()}.$ext")
                FileOutputStream(currentTempFile!!).use { it.write(mp3Data) }
                currentTempFile!!.setReadable(true, false)

                Log.d(TAG, "Playing MP3 (${mp3Data.size} bytes) from ${currentTempFile!!.absolutePath}")

                if (useVoicePool) {
                    playWithVoicePool(currentTempFile!!)
                } else {
                    playWithMediaPlayer(currentTempFile!!)
                }
            } catch (e: Exception) {
                Log.e(TAG, "MP3 playback error", e)
                cleanupTempFile()
                _state = PlaybackState.IDLE
                withContext(Dispatchers.Main) {
                    listener?.onPlaybackError(e.message ?: "Playback error")
                }
                false
            }
        }
    }

    /**
     * Play using robot's VoicePool
     */
    private suspend fun playWithVoicePool(file: File): Boolean {
        return suspendCancellableCoroutine { continuation ->
            _state = PlaybackState.PLAYING

            scope.launch(Dispatchers.Main) {
                listener?.onPlaybackStarted()
            }

            VoicePool.get().playLocalTTs(file, ResourcePolicy.Exclusive, object : VoiceListener {
                override fun onCompleted() {
                    Log.d(TAG, "VoicePool playback completed")
                    cleanupTempFile()
                    _state = PlaybackState.IDLE

                    scope.launch(Dispatchers.Main) {
                        listener?.onPlaybackComplete()
                    }

                    if (continuation.isActive) {
                        continuation.resume(true) {}
                    }
                }

                override fun onError(code: Int, message: String) {
                    Log.e(TAG, "VoicePool error: $code - $message")

                    // Fallback to MediaPlayer
                    scope.launch {
                        try {
                            val result = playWithMediaPlayer(file)
                            if (continuation.isActive) {
                                continuation.resume(result) {}
                            }
                        } catch (e: Exception) {
                            cleanupTempFile()
                            _state = PlaybackState.IDLE
                            if (continuation.isActive) {
                                continuation.resume(false) {}
                            }
                        }
                    }
                }
            })

            continuation.invokeOnCancellation {
                try {
                    VoicePool.get().stopTTs(ResourcePolicy.Exclusive, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping VoicePool", e)
                }
                cleanupTempFile()
            }
        }
    }

    /**
     * Play using MediaPlayer
     */
    private suspend fun playWithMediaPlayer(file: File): Boolean {
        return suspendCancellableCoroutine { continuation ->
            _state = PlaybackState.PLAYING

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )

                setDataSource(file.absolutePath)

                setOnPreparedListener { mp ->
                    scope.launch(Dispatchers.Main) {
                        listener?.onPlaybackStarted()
                    }
                    mp.start()
                }

                setOnCompletionListener { mp ->
                    Log.d(TAG, "MediaPlayer playback completed")
                    mp.release()
                    mediaPlayer = null
                    cleanupTempFile()
                    _state = PlaybackState.IDLE

                    scope.launch(Dispatchers.Main) {
                        listener?.onPlaybackComplete()
                    }

                    if (continuation.isActive) {
                        continuation.resume(true) {}
                    }
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: $what, $extra")
                    mp.release()
                    mediaPlayer = null
                    cleanupTempFile()
                    _state = PlaybackState.IDLE

                    scope.launch(Dispatchers.Main) {
                        listener?.onPlaybackError("MediaPlayer error: $what")
                    }

                    if (continuation.isActive) {
                        continuation.resume(false) {}
                    }
                    true
                }

                prepareAsync()
            }

            continuation.invokeOnCancellation {
                mediaPlayer?.let {
                    try {
                        if (it.isPlaying) it.stop()
                        it.release()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping MediaPlayer", e)
                    }
                }
                mediaPlayer = null
                cleanupTempFile()
            }
        }
    }

    /**
     * Stop playback immediately (for barge-in)
     */
    fun stop() {
        stopRequested = true
        _state = PlaybackState.STOPPED

        // Stop AudioTrack
        stopAudioTrack()

        // Stop VoicePool
        try {
            VoicePool.get().stopTTs(ResourcePolicy.Exclusive, null)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping VoicePool", e)
        }

        // Stop MediaPlayer
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaPlayer", e)
            }
        }
        mediaPlayer = null

        // Cancel job
        playbackJob?.cancel()
        playbackJob = null

        cleanupTempFile()

        _state = PlaybackState.IDLE
        Log.d(TAG, "Playback stopped")
    }

    private fun stopAudioTrack() {
        audioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping AudioTrack", e)
            }
        }
        audioTrack = null
    }

    private fun cleanupTempFile() {
        currentTempFile?.delete()
        currentTempFile = null
    }

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = _state == PlaybackState.PLAYING

    /**
     * Get estimated duration for PCM data
     */
    fun estimateDurationMs(pcmBytes: Int): Long {
        return (pcmBytes.toLong() * 1000) / (config.sampleRate * 2)
    }

    /**
     * Release all resources
     */
    fun release() {
        stop()
        scope.cancel()
    }
}

/**
 * Audio format detection
 */
object AudioFormatDetector {
    fun isMP3(data: ByteArray): Boolean {
        if (data.size < 4) return false

        // Check for ID3 tag
        if (data[0] == 'I'.code.toByte() &&
            data[1] == 'D'.code.toByte() &&
            data[2] == '3'.code.toByte()) {
            return true
        }

        // Check for MP3 frame sync
        if (data[0] == 0xFF.toByte() && (data[1].toInt() and 0xE0) == 0xE0) {
            return true
        }

        return false
    }

    fun isWAV(data: ByteArray): Boolean {
        if (data.size < 4) return false
        return data[0] == 'R'.code.toByte() &&
                data[1] == 'I'.code.toByte() &&
                data[2] == 'F'.code.toByte() &&
                data[3] == 'F'.code.toByte()
    }

    fun isPCM(data: ByteArray): Boolean {
        return !isMP3(data) && !isWAV(data)
    }
}
