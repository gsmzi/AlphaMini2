package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import org.json.JSONObject

/**
 * On-device speech recognition using Vosk.
 * Replaces the HTTP call to the LLM server's /v1/audio/chat transcription step.
 *
 * Uses StorageService.sync() (synchronous) to unpack models from assets,
 * then constructs Model objects from the unpacked paths.
 */
class LocalSpeechRecognizer(private val context: Context) {
    companion object {
        private const val TAG = "LocalSpeechRecognizer"
        private const val SAMPLE_RATE = 16000f
        private const val CHUNK_SIZE = 4000
    }

    private var modelEn: Model? = null
    private var modelDe: Model? = null

    @Volatile
    var isReady = false
        private set

    /**
     * Initialize Vosk models from assets. Call once at startup.
     * Uses synchronous StorageService.sync() on IO dispatcher.
     * First launch unpacks ~80MB from assets (10-20s), subsequent launches are fast.
     */
    suspend fun initialize(
        loadEnglish: Boolean = true,
        loadGerman: Boolean = true,
        onProgress: ((String) -> Unit)? = null
    ) {
        try {
            if (loadEnglish) {
                onProgress?.invoke("Loading English model...")
                Log.d(TAG, "Syncing English Vosk model from assets...")
                modelEn = loadModel("model-en-us")
                Log.d(TAG, "English Vosk model: ${if (modelEn != null) "READY" else "FAILED"}")
            }

            if (loadGerman) {
                onProgress?.invoke("Loading German model...")
                Log.d(TAG, "Syncing German Vosk model from assets...")
                modelDe = loadModel("model-de")
                Log.d(TAG, "German Vosk model: ${if (modelDe != null) "READY" else "FAILED"}")
            }

            isReady = modelEn != null || modelDe != null
            onProgress?.invoke(if (isReady) "Speech recognition ready" else "Speech recognition FAILED")
            Log.d(TAG, "LocalSpeechRecognizer initialized: isReady=$isReady (en=${modelEn != null}, de=${modelDe != null})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk models", e)
            isReady = false
        }
    }

    /**
     * Synchronously unpack a Vosk model from assets and construct Model object.
     * StorageService.sync() extracts the asset folder to app-internal storage
     * and returns the path. Model() loads it into memory.
     */
    private suspend fun loadModel(assetPath: String): Model? =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "StorageService.sync($assetPath) starting...")
                val outputPath = StorageService.sync(context, assetPath, assetPath)
                Log.d(TAG, "StorageService.sync($assetPath) => $outputPath")
                val model = Model(outputPath)
                Log.d(TAG, "Model($outputPath) created successfully")
                model
            } catch (e: java.io.IOException) {
                Log.e(TAG, "IOException unpacking model '$assetPath'", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model '$assetPath'", e)
                null
            }
        }

    /**
     * Transcribe raw PCM audio bytes (16kHz, 16-bit, mono) to text.
     * Returns null if nothing recognized or models not ready.
     */
    suspend fun transcribe(audioBytes: ByteArray, language: String = "en"): String? =
        withContext(Dispatchers.IO) {
            val model = if (language.startsWith("de")) modelDe ?: modelEn else modelEn ?: modelDe

            if (model == null) {
                Log.e(TAG, "No Vosk model available for language: $language (isReady=$isReady)")
                return@withContext null
            }

            try {
                Log.d(TAG, "Transcribing [$language] ${audioBytes.size} bytes...")

                val recognizer = Recognizer(model, SAMPLE_RATE)
                try {
                    // Feed audio in chunks
                    var offset = 0
                    while (offset < audioBytes.size) {
                        val end = minOf(offset + CHUNK_SIZE, audioBytes.size)
                        val chunk = audioBytes.copyOfRange(offset, end)
                        recognizer.acceptWaveForm(chunk, chunk.size)
                        offset = end
                    }

                    val result = JSONObject(recognizer.finalResult)
                    val text = result.optString("text", "").trim()

                    Log.d(TAG, "Heard: '$text'")
                    if (text.isNotEmpty()) text else null
                } finally {
                    recognizer.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription error", e)
                null
            }
        }

    fun release() {
        modelEn?.close()
        modelEn = null
        modelDe?.close()
        modelDe = null
        isReady = false
        Log.d(TAG, "LocalSpeechRecognizer released")
    }
}
