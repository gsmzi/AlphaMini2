package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Simple OpenAI Chat Completions client for the Alpha Mini robot.
 * Uses gpt-4o-mini for fast, cheap responses.
 */
class OpenAiClient(private val apiKey: String) {
    companion object {
        private const val TAG = "OpenAiClient"
        private const val URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val SYSTEM_PROMPT_EN = """You are Alpha Mini, a small friendly robot made by UBTECH.
Keep responses to 1-2 short sentences maximum. Be fun, playful, and engaging.
You can do actions like dance, wave, bow, clap, and hands up.
If someone asks you to do something physical, mention you can do it.
Never use markdown, emojis, or special formatting — your response will be spoken aloud."""

        private const val SYSTEM_PROMPT_DE = """Du bist Alpha Mini, ein kleiner freundlicher Roboter von UBTECH.
Halte Antworten auf maximal 1-2 kurze Sätze. Sei lustig, verspielt und unterhaltsam.
Du kannst tanzen, winken, verbeugen, klatschen und die Hände hochheben.
Verwende niemals Markdown, Emojis oder Sonderformatierungen — deine Antwort wird laut vorgelesen."""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Keep last few turns for context within a session
    private val conversationHistory = mutableListOf<Pair<String, String>>() // role, content
    private val maxHistory = 6 // keep last 3 exchanges

    /**
     * Send user message to OpenAI and get a response.
     * @param message The user's transcribed speech
     * @param language "de" or "en"
     * @return Response text, or null on failure
     */
    suspend fun chat(message: String, language: String): String? = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()
            val systemPrompt = if (language.startsWith("de")) SYSTEM_PROMPT_DE else SYSTEM_PROMPT_EN

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))

                // Add conversation history for context
                for ((role, content) in conversationHistory) {
                    put(JSONObject().put("role", role).put("content", content))
                }

                put(JSONObject().put("role", "user").put("content", message))
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("max_tokens", 100)
                put("temperature", 0.8)
            }

            val request = Request.Builder()
                .url(URL)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "OpenAI error ${response.code}: ${responseBody?.take(200)}")
                return@withContext null
            }

            val json = JSONObject(responseBody ?: "")
            val text = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            val elapsed = System.currentTimeMillis() - t0
            Log.d(TAG, "OpenAI response (${elapsed}ms): '${text.take(80)}'")

            // Save to history
            conversationHistory.add("user" to message)
            conversationHistory.add("assistant" to text)
            while (conversationHistory.size > maxHistory) {
                conversationHistory.removeAt(0)
            }

            text.ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI request failed", e)
            null
        }
    }

    /** Clear conversation history (call on new session). */
    fun clearHistory() {
        conversationHistory.clear()
    }
}
