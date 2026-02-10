package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Wrapper around Android's SpeechRecognizer (Google online STT).
 * Uses the device's built-in mic — no separate AudioRecord needed.
 * Must be created and used on the main thread (Android requirement).
 */
class GoogleSpeechRecognizer(private val context: Context) {
    companion object {
        private const val TAG = "GoogleSTT"
    }

    private var recognizer: SpeechRecognizer? = null

    @Volatile
    var isReady = false
        private set

    /**
     * Initialize. Must be called on the main thread.
     */
    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            isReady = false
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        isReady = recognizer != null
        Log.d(TAG, "GoogleSpeechRecognizer initialized: isReady=$isReady")
    }

    /**
     * Start listening and return the recognized text.
     * Suspends until recognition completes, an error occurs, or is cancelled.
     * Switches to Main thread internally (Android SpeechRecognizer requirement).
     *
     * @param language "de" or "en"
     * @return recognized text, or null if nothing heard / error
     */
    suspend fun listen(language: String): String? = withContext(Dispatchers.Main) {
        val rec = recognizer
        if (rec == null) {
            Log.e(TAG, "SpeechRecognizer not initialized")
            return@withContext null
        }

        suspendCancellableCoroutine { cont ->
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    if (language.startsWith("de")) "de-DE" else "en-US"
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Silence detection timeouts
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1500L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1000L
                )
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }

            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech ended")
                }

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "No permission"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        else -> "Unknown error $error"
                    }
                    Log.w(TAG, "Recognition error: $errorMsg ($error)")
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResults(results: Bundle?) {
                    val matches =
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    Log.d(TAG, "Recognition result: '$text'")
                    if (cont.isActive) cont.resume(if (text.isNullOrBlank()) null else text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial =
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.d(TAG, "Partial: ${partial?.firstOrNull()}")
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            rec.startListening(intent)
            Log.d(TAG, "Listening started [${if (language.startsWith("de")) "de-DE" else "en-US"}]")

            cont.invokeOnCancellation {
                Log.d(TAG, "Listening cancelled")
                try {
                    rec.cancel()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Cancel any active recognition.
     */
    fun cancel() {
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Cancel failed", e)
        }
    }

    fun release() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Release failed", e)
        }
        recognizer = null
        isReady = false
        Log.d(TAG, "GoogleSpeechRecognizer released")
    }
}
