package com.ubtrobot.mini.sdkdemo.voicedialogue.audio

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

/**
 * Audio playback format type
 */
enum class PlaybackFormat {
    PCM,    // Raw PCM audio
    MP3,    // MP3 encoded audio
    WAV,    // WAV audio
    AUTO    // Auto-detect from data
}

/**
 * Audio player abstraction for voice dialogue
 * Supports AudioTrack (PCM), MediaPlayer (MP3/WAV), and robot VoicePool
 */
class DialogueAudioPlayer(
    private val context: Context,
    private val sampleRate: Int = 16000,
    private val preferVoicePool: Boolean = true
) {
    companion object {
        private const val TAG = "DialogueAudioPlayer"
    }

    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioFile: File? = null
    private var isPlayingAudio = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Play audio data
     * @param audioData Audio bytes (PCM or MP3)
     * @param onComplete Callback when playback completes
     * @param format Audio format (auto-detected if AUTO)
     */
    suspend fun play(
        audioData: ByteArray,
        onComplete: (() -> Unit)? = null,
        format: PlaybackFormat = PlaybackFormat.AUTO
    ) {
        stop()

        val detectedFormat = if (format == PlaybackFormat.AUTO) {
            detectFormat(audioData)
        } else {
            format
        }

        when (detectedFormat) {
            PlaybackFormat.PCM -> playPcm(audioData, onComplete)
            PlaybackFormat.MP3, PlaybackFormat.WAV -> playMediaFile(audioData, detectedFormat, onComplete)
            PlaybackFormat.AUTO -> playMediaFile(audioData, PlaybackFormat.MP3, onComplete)
        }
    }

    /**
     * Play PCM audio using AudioTrack
     */
    private suspend fun playPcm(pcmData: ByteArray, onComplete: (() -> Unit)?) {
        withContext(Dispatchers.IO) {
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
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
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
                isPlayingAudio = true
                Log.d(TAG, "Playing PCM audio (${pcmData.size} bytes)")

                // Write audio data
                var offset = 0
                while (offset < pcmData.size && isPlayingAudio) {
                    val writeSize = minOf(bufferSize, pcmData.size - offset)
                    val written = audioTrack?.write(pcmData, offset, writeSize) ?: 0
                    if (written > 0) {
                        offset += written
                    } else {
                        break
                    }
                }

                // Wait for playback to complete
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
                isPlayingAudio = false

                Log.d(TAG, "PCM playback completed")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }

            } catch (e: Exception) {
                Log.e(TAG, "PCM playback error", e)
                isPlayingAudio = false
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    /**
     * Play media file (MP3/WAV) using VoicePool or MediaPlayer
     */
    private suspend fun playMediaFile(
        audioData: ByteArray,
        format: PlaybackFormat,
        onComplete: (() -> Unit)?
    ) {
        try {
            // Save to temp file
            val extension = when (format) {
                PlaybackFormat.MP3 -> "mp3"
                PlaybackFormat.WAV -> "wav"
                else -> "mp3"
            }

            // Use external cache dir for VoicePool access (runs in different process)
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val tempFile = File(cacheDir, "dialogue_audio.$extension")
            withContext(Dispatchers.IO) {
                FileOutputStream(tempFile).use { it.write(audioData) }
                // Make readable by VoicePool service (different process)
                tempFile.setReadable(true, false)
                tempFile.setWritable(true, false)
            }
            currentAudioFile = tempFile

            Log.d(TAG, "Playing media file: ${tempFile.absolutePath} (${audioData.size} bytes)")

            // Try VoicePool first (robot speaker)
            if (preferVoicePool) {
                try {
                    playWithVoicePool(tempFile, onComplete)
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "VoicePool failed, falling back to MediaPlayer", e)
                }
            }

            // Fallback to MediaPlayer
            playWithMediaPlayer(tempFile, onComplete)

        } catch (e: Exception) {
            Log.e(TAG, "Media playback error", e)
            isPlayingAudio = false
            onComplete?.invoke()
        }
    }

    /**
     * Play using robot's VoicePool
     */
    private fun playWithVoicePool(file: File, onComplete: (() -> Unit)?) {
        isPlayingAudio = true

        // Verify file exists before playing
        Log.d(TAG, "VoicePool playing file: ${file.absolutePath}, exists=${file.exists()}, size=${file.length()}")

        VoicePool.get().playLocalTTs(file, ResourcePolicy.Exclusive, object : VoiceListener {
            override fun onCompleted() {
                Log.d(TAG, "VoicePool playback completed")
                isPlayingAudio = false
                cleanupTempFile()
                onComplete?.invoke()
            }

            override fun onError(code: Int, message: String) {
                Log.e(TAG, "VoicePool error: $code - $message, falling back to MediaPlayer")
                isPlayingAudio = false
                // DON'T cleanup file here - MediaPlayer needs it!
                // Try MediaPlayer as fallback
                scope.launch {
                    try {
                        playWithMediaPlayer(file, onComplete)
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaPlayer fallback also failed", e)
                        cleanupTempFile()
                        onComplete?.invoke()
                    }
                }
            }
        })
    }

    /**
     * Play using MediaPlayer
     */
    private fun playWithMediaPlayer(file: File, onComplete: (() -> Unit)?) {
        isPlayingAudio = true

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .build()
            )
            setDataSource(file.absolutePath)

            setOnCompletionListener { mp ->
                Log.d(TAG, "MediaPlayer playback completed")
                isPlayingAudio = false
                cleanupTempFile()
                mp.release()
                mediaPlayer = null
                onComplete?.invoke()
            }

            setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error: $what, $extra")
                isPlayingAudio = false
                cleanupTempFile()
                mp.release()
                mediaPlayer = null
                onComplete?.invoke()
                true
            }

            prepare()
            start()
        }
    }

    /**
     * Stop playback
     */
    fun stop() {
        isPlayingAudio = false

        try {
            // Stop VoicePool
            VoicePool.get().stopTTs(ResourcePolicy.Exclusive, null)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping VoicePool", e)
        }

        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack", e)
        }

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaPlayer", e)
        }

        cleanupTempFile()
        Log.d(TAG, "Playback stopped")
    }

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = isPlayingAudio

    /**
     * Get estimated audio duration in milliseconds
     */
    fun getEstimatedDurationMs(audioData: ByteArray, format: PlaybackFormat): Long {
        return when (format) {
            PlaybackFormat.PCM -> {
                // PCM: bytes / (sampleRate * 2 bytes per sample) * 1000
                (audioData.size.toLong() * 1000) / (sampleRate * 2)
            }
            PlaybackFormat.MP3 -> {
                // Rough estimate for MP3 at 128kbps: bytes * 8 / 128000 * 1000
                (audioData.size.toLong() * 8 * 1000) / 128000
            }
            else -> 0
        }
    }

    private fun cleanupTempFile() {
        currentAudioFile?.delete()
        currentAudioFile = null
    }

    /**
     * Detect audio format from data
     */
    private fun detectFormat(data: ByteArray): PlaybackFormat {
        if (data.size < 4) return PlaybackFormat.PCM

        // Check for MP3 magic bytes (ID3 or frame sync)
        if ((data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()) ||
            (data[0] == 0xFF.toByte() && (data[1].toInt() and 0xE0) == 0xE0)
        ) {
            return PlaybackFormat.MP3
        }

        // Check for WAV magic bytes
        if (data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() &&
            data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte()
        ) {
            return PlaybackFormat.WAV
        }

        // Default to PCM
        return PlaybackFormat.PCM
    }

    /**
     * Release resources
     */
    fun release() {
        stop()
        scope.cancel()
    }
}
