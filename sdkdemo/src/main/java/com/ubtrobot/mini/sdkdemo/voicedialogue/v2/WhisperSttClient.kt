package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit

/**
 * Sends recorded PCM audio to the OpenAI Whisper API for transcription.
 * Returns null on any failure so the caller can fall back to Vosk.
 */
class WhisperSttClient(private val apiKey: String) {
    companion object {
        private const val TAG = "WhisperSttClient"
        private const val URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val MODEL = "whisper-1"

        // 16-bit PCM RMS threshold below which audio is treated as silence.
        // Observed values: noise < 190, real speech > 240. 200 gives a clean gap.
        private const val MIN_RMS = 200.0

        // Phrases Whisper commonly hallucinates when fed silence or background noise.
        private val HALLUCINATION_PATTERNS = listOf(
            "untertitel",                 // "Untertitel der Amara.org-Community", "Untertitelung im Auftrag des ZDF…"
            "abonniert den kanal",
            "aktiviert die glocke",
            "vielen dank für",
            "vielen dank fürs",
            "like and subscribe",
            "please subscribe",
            "thank you for watching",
            "thanks for watching",
            "subtitles by",
            "transcribed by",
            "www.",
            "♪", "[ musik ]", "[musik]",
            "[applaus]", "[gelächter]", "[laughter]", "[music]"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Transcribe raw PCM audio via the Whisper API.
     * @param pcm   Raw 16-bit mono PCM bytes
     * @param sampleRate  Sample rate of the recording (e.g. 16000)
     * @param language    BCP-47 language code, e.g. "en" or "de"
     * @return Transcribed text, or null on failure
     */
    suspend fun transcribe(pcm: ByteArray, sampleRate: Int, language: String): String? =
        withContext(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            try {
                // Gate 1: energy check — skip API call if audio is mostly silence
                val rms = calculateRms(pcm)
                Log.d(TAG, "Audio RMS=${"%.1f".format(rms)} (threshold=$MIN_RMS)")
                if (rms < MIN_RMS) {
                    Log.w(TAG, "Audio energy too low — silence/noise, skipping Whisper")
                    return@withContext null
                }

                val wav = pcmToWav(pcm, sampleRate)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file", "speech.wav",
                        wav.toRequestBody("audio/wav".toMediaType())
                    )
                    .addFormDataPart("model", MODEL)
                    .addFormDataPart("language", language)
                    .build()

                val request = Request.Builder()
                    .url(URL)
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    Log.e(TAG, "Whisper error ${response.code}: ${responseBody?.take(200)}")
                    return@withContext null
                }

                val text = JSONObject(responseBody ?: "").getString("text").trim()
                val elapsed = System.currentTimeMillis() - t0
                Log.d(TAG, "transcribed in ${elapsed}ms: '${text.take(80)}'")

                // Gate 2: hallucination filter — Whisper invents subtitle/YouTube text for noise
                if (isHallucination(text)) {
                    Log.w(TAG, "Hallucination detected, discarding: '${text.take(80)}'")
                    return@withContext null
                }

                text.ifBlank { null }
            } catch (e: Exception) {
                Log.e(TAG, "Whisper request failed", e)
                null
            }
        }

    /**
     * RMS energy of 16-bit little-endian mono PCM.
     * Returns 0 for empty or malformed input.
     */
    private fun calculateRms(pcm: ByteArray): Double {
        if (pcm.size < 2) return 0.0
        var sumSq = 0.0
        var i = 0
        while (i < pcm.size - 1) {
            val raw = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val sample = if (raw > 32767) raw - 65536 else raw
            sumSq += sample.toDouble() * sample.toDouble()
            i += 2
        }
        return Math.sqrt(sumSq / (pcm.size / 2))
    }

    /** True if the text matches a known Whisper hallucination pattern. */
    private fun isHallucination(text: String): Boolean {
        val lower = text.lowercase()
        return HALLUCINATION_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Prepend a 44-byte WAV header to raw PCM bytes.
     * Assumes 16-bit mono PCM.
     */
    private fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * 2  // 16-bit mono
        val dataSize = pcm.size
        val totalSize = 36 + dataSize

        val out = ByteArrayOutputStream(44 + dataSize)
        val dos = DataOutputStream(out)

        // RIFF header
        dos.writeBytes("RIFF")
        writeLe32(dos, totalSize)
        dos.writeBytes("WAVE")

        // fmt chunk
        dos.writeBytes("fmt ")
        writeLe32(dos, 16)          // chunk size
        writeLe16(dos, 1)           // PCM format
        writeLe16(dos, 1)           // mono
        writeLe32(dos, sampleRate)
        writeLe32(dos, byteRate)
        writeLe16(dos, 2)           // block align (16-bit mono = 2 bytes)
        writeLe16(dos, 16)          // bits per sample

        // data chunk
        dos.writeBytes("data")
        writeLe32(dos, dataSize)
        dos.write(pcm)

        return out.toByteArray()
    }

    private fun writeLe32(dos: DataOutputStream, value: Int) {
        dos.write(value and 0xFF)
        dos.write((value shr 8) and 0xFF)
        dos.write((value shr 16) and 0xFF)
        dos.write((value shr 24) and 0xFF)
    }

    private fun writeLe16(dos: DataOutputStream, value: Int) {
        dos.write(value and 0xFF)
        dos.write((value shr 8) and 0xFF)
    }
}
