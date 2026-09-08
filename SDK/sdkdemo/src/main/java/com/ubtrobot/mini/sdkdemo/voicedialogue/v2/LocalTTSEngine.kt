package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.ubtrobot.master.component.ResourcePolicy
import com.ubtrobot.mini.voice.VoiceListener
import com.ubtrobot.mini.voice.VoicePool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Local TTS engine using Android TextToSpeech and MediaPlayer.
 *
 * This synthesizes speech to a temporary WAV file and plays it back
 * using MediaPlayer. It keeps the app fully on-device (no PC servers).
 */
class LocalTTSEngine(private val context: Context) {
    companion object {
        private const val TAG = "LocalTTSEngine"
        private const val SYNTH_TIMEOUT_MS = 15000L
    }

    private enum class Mode {
        ANDROID_TTS,
        VOICE_POOL
    }

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private val utteranceCounter = AtomicInteger(0)
    private var currentLocale: Locale = Locale.US
    private var mode: Mode? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    var isReady = false
        private set

    /**
     * Initialize TextToSpeech engine.
     */
    fun initialize() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = tts?.setLanguage(currentLocale)
                isReady = langResult != TextToSpeech.LANG_MISSING_DATA &&
                    langResult != TextToSpeech.LANG_NOT_SUPPORTED
                mode = if (isReady) Mode.ANDROID_TTS else mode
                Log.d(TAG, "TextToSpeech ready (locale=$currentLocale, ready=$isReady)")
                if (!isReady) {
                    tryVoicePoolFallback()
                }
            } else {
                Log.e(TAG, "TextToSpeech init failed: $status")
                isReady = false
                tryVoicePoolFallback()
            }
        }
    }

    /**
     * Set TTS language based on current UI selection.
     */
    fun setLanguage(language: DialogueConfig.Language) {
        currentLocale = if (language == DialogueConfig.Language.DE) Locale.GERMANY else Locale.US
        tts?.setLanguage(currentLocale)
        Log.d(TAG, "TTS language set to $currentLocale")
    }

    fun isAndroidTtsReady(): Boolean {
        return mode == Mode.ANDROID_TTS && isReady
    }

    fun logAvailableVoices() {
        val engine = tts
        if (engine == null) {
            Log.w(TAG, "TTS engine not initialized; cannot list voices")
            return
        }
        try {
            val voices = engine.voices?.map { it.name }?.sorted() ?: emptyList()
            Log.d(TAG, "Available voices (${voices.size}): ${voices.joinToString(", ")}")
            val locales = engine.availableLanguages?.map { it.toLanguageTag() }?.sorted() ?: emptyList()
            Log.d(TAG, "Available locales (${locales.size}): ${locales.joinToString(", ")}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query TTS voices/locales", e)
        }
    }

    fun speakTestOncePerBoot(text: String) {
        if (!isAndroidTtsReady()) {
            Log.w(TAG, "TTS not ready; skipping boot test")
            return
        }
        scope.launch {
            try {
                speakText(text)
            } catch (e: Exception) {
                Log.w(TAG, "Boot TTS test failed", e)
            }
        }
    }

    /**
     * Speak text using TextToSpeech -> WAV -> MediaPlayer.
     * Suspends until speech completes. Returns true on success.
     */
    suspend fun speakText(text: String): Boolean {
        if (!isReady) {
            Log.e(TAG, "TTS not ready")
            return false
        }
        if (text.isBlank()) {
            Log.d(TAG, "Empty text, skipping TTS")
            return true
        }

        if (mode == Mode.VOICE_POOL) {
            return speakWithVoicePool(text)
        }

        val ttsEngine = tts ?: return false
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val outFile = File(cacheDir, "tts_${System.currentTimeMillis()}.wav")
        val utteranceId = "utt_${utteranceCounter.incrementAndGet()}"

        val synthDone = CompletableDeferred<Boolean>()
        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                synthDone.complete(true)
            }
            override fun onError(utteranceId: String?) {
                synthDone.complete(false)
            }
        })

        try {
            Log.d(TAG, "Synthesizing: '${text.take(60)}...'")
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val synthResult = ttsEngine.synthesizeToFile(text, params, outFile, utteranceId)
            if (synthResult != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS synthesizeToFile failed: $synthResult")
                return false
            }

            val synthOk = try {
                withTimeout(SYNTH_TIMEOUT_MS) { synthDone.await() }
            } catch (e: Exception) {
                Log.e(TAG, "TTS synthesis timeout", e)
                false
            }
            if (!synthOk || !outFile.exists()) {
                Log.e(TAG, "TTS synthesis failed or file missing")
                return false
            }

            return suspendCancellableCoroutine { cont ->
                try {
                    stop()
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                .build()
                        )
                        setDataSource(outFile.absolutePath)
                        setOnPreparedListener { mp ->
                            mp.start()
                        }
                        setOnCompletionListener { mp ->
                            mp.release()
                            mediaPlayer = null
                            outFile.delete()
                            if (cont.isActive) cont.resume(true)
                        }
                        setOnErrorListener { mp, what, extra ->
                            Log.e(TAG, "MediaPlayer error: $what/$extra")
                            mp.release()
                            mediaPlayer = null
                            outFile.delete()
                            if (cont.isActive) cont.resume(false)
                            true
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Playback failed", e)
                    outFile.delete()
                    if (cont.isActive) cont.resume(false)
                }

                cont.invokeOnCancellation {
                    stop()
                    outFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed", e)
            outFile.delete()
            return false
        }
    }

    private suspend fun speakWithVoicePool(text: String): Boolean {
        return suspendCancellableCoroutine { cont ->
            try {
                Log.d(TAG, "VoicePool speaking: '${text.take(60)}...'")
                VoicePool.get().playTTs(text, ResourcePolicy.Exclusive, object : VoiceListener {
                    override fun onCompleted() {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(code: Int, message: String?) {
                        Log.e(TAG, "VoicePool TTS error: $code - $message")
                        if (cont.isActive) cont.resume(false)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "VoicePool.playTTs failed", e)
                if (cont.isActive) cont.resume(false)
            }

            cont.invokeOnCancellation {
                try {
                    VoicePool.get().stopTTs(ResourcePolicy.Exclusive, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping VoicePool TTS", e)
                }
            }
        }
    }

    /**
     * Stop any current TTS playback.
     */
    fun stop() {
        try {
            if (mode == Mode.VOICE_POOL) {
                VoicePool.get().stopTTs(ResourcePolicy.Exclusive, null)
            }
            tts?.stop()
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        mode = null
        scope.cancel()
        isReady = false
        Log.d(TAG, "LocalTTSEngine released")
    }

    private fun tryVoicePoolFallback() {
        try {
            VoicePool.get()
            mode = Mode.VOICE_POOL
            isReady = true
            Log.d(TAG, "VoicePool fallback ready")
        } catch (e: Exception) {
            Log.e(TAG, "VoicePool not available", e)
        }
    }
}
