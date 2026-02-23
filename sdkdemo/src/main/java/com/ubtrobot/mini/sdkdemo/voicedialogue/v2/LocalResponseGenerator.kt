package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device rule-based response generator.
 * Direct Kotlin port of llm_server/server.py generate_rule_based_response().
 */
class LocalResponseGenerator {
    companion object {
        private const val TAG = "LocalResponseGenerator"

        // ═══════════════════════════════════════════════════════════════
        // EMOTION MAP — matches Python server exactly
        // ═══════════════════════════════════════════════════════════════
        val EMOTION_MAP = mapOf(
            "happy" to EmotionData("emo_007", "010", "green", "\u5f00\u5fc3"),
            "sad" to EmotionData("emo_014", "016", "blue", "\u62b1\u6b49"),
            "thinking" to EmotionData("emo_010", "021", "blue", "\u601d\u8003"),
            "excited" to EmotionData("emo_008", "014", "purple", "\u5174\u594b"),
            "surprised" to EmotionData("codemao8", "018", "yellow", "\u60ca\u8bb6"),
            "neutral" to EmotionData("normal_1", "011", "normal", "\u4e2d\u6027"),
            "comfort" to EmotionData("emo_006", "011", "normal", "\u5b89\u6170")
        )

        // ═══════════════════════════════════════════════════════════════
        // ACTION OVERRIDE MAP — matches Python server exactly
        // ═══════════════════════════════════════════════════════════════
        val ACTION_OVERRIDE_MAP = mapOf(
            "dance" to "014",
            "wave" to "010",
            "hands_up" to "017",
            "clap" to "018",
            "bow" to "016",
            "nod" to "011",
            "think" to "021",
            "idle" to "011",
            "greeting" to "010"
        )

        // ═══════════════════════════════════════════════════════════════
        // STOP PHRASES
        // ═══════════════════════════════════════════════════════════════
        val STOP_PHRASES_EN = listOf(
            "goodbye", "bye bye", "that's all", "thats all", "go to sleep",
            "i'm done", "im done", "thank you bye", "end conversation", "quit",
            "that is all", "no more", "nothing else", "i'm finished"
        )

        val STOP_PHRASES_DE = listOf(
            "tsch\u00fcss", "tschuss", "tschuess",
            "auf wiedersehen", "wiedersehen", "das war's", "das wars",
            "ich bin fertig", "danke tsch\u00fcss", "beenden",
            "nichts mehr", "das reicht",
            "ciao", "bye bye", "tschau"
        )

        // ═══════════════════════════════════════════════════════════════
        // ACTION WORD LISTS — tight matching only (OpenAI handles the rest)
        // ═══════════════════════════════════════════════════════════════
        val DANCE_WORDS = listOf(
            "dance", "dances", "dancing", "danced", "dancer",
            "tanz", "tanzen", "tanzt", "tai chi",
            "can you dance", "let's dance", "do a dance", "show me a dance", "dance for me"
        )

        val WAVE_WORDS = listOf(
            "wave", "waves", "waving", "waved",
            "winke", "winken", "winkt",
            "wave at me", "wave hello", "wave your hand"
        )

        val HANDS_UP_WORDS = listOf(
            "hands up", "hand up", "hands-up", "handsup", "raise hands", "raise your hands",
            "put your hands up", "arms up",
            "h\u00e4nde hoch", "hande hoch", "arme hoch", "haende hoch",
            "hands in the air", "put them up", "stick em up"
        )

        val CLAP_WORDS = listOf(
            "clap", "claps", "clapping", "clapped",
            "klatsch", "klatschen", "applaud", "applause",
            "clap your hands", "give me a clap"
        )

        val BOW_WORDS = listOf(
            "bow", "bows", "bowing", "bowed",
            "verbeugen", "verbeug", "verbeugung",
            "take a bow"
        )

        // Greetings, jokes, help, thanks etc. are all handled by OpenAI now

        // ═══════════════════════════════════════════════════════════════
        // FALLBACK RESPONSES — fun "didn't hear you" phrases
        // ═══════════════════════════════════════════════════════════════
        data class FallbackEntry(val speech: String, val emotion: String, val action: String)

        val FALLBACK_EN = listOf(
            FallbackEntry("Hmm, I didn't catch that. Could you say it again?", "thinking", "think"),
            FallbackEntry("Oops, my ears must be ringing! Try again?", "surprised", "wave"),
            FallbackEntry("Sorry, I was daydreaming! What did you say?", "happy", "hands_up"),
            FallbackEntry("I think I heard a ghost! Say that again?", "surprised", "clap"),
            FallbackEntry("My brain just buffered! One more time?", "thinking", "think")
        )

        val FALLBACK_DE = listOf(
            FallbackEntry("Hmm, das hab ich nicht verstanden. Kannst du das nochmal sagen?", "thinking", "think"),
            FallbackEntry("Ups, ich glaube meine Ohren klingeln! Nochmal bitte?", "surprised", "wave"),
            FallbackEntry("Was hast du gesagt? Ich war kurz abgelenkt!", "happy", "hands_up"),
            FallbackEntry("Ich glaube ich habe einen Geist geh\u00f6rt! Sag das nochmal?", "surprised", "clap"),
            FallbackEntry("Mein Gehirn hat kurz gepuffert! Noch einmal bitte?", "thinking", "think")
        )
    }

    data class EmotionData(
        val expression: String,
        val action: String,
        val light: String,
        val chinese: String
    )

    data class LocalResponse(
        val speech: String,
        val emotion: String,
        val action: String,
        val keepSession: Boolean = true,
        val conversationEnd: Boolean = false,
        val askFollowup: Boolean = false
    )

    fun isStopPhrase(text: String): Boolean {
        val words = text.lowercase().trim()
        return (STOP_PHRASES_EN + STOP_PHRASES_DE).any { phrase ->
            // Match as whole words/phrase, not substring
            words == phrase || words.startsWith("$phrase ") ||
                words.endsWith(" $phrase") || " $phrase " in words
        }
    }

    /**
     * Generate a fun fallback response when no speech was detected or input was unrecognized.
     */
    fun generateFallbackResponse(language: String = "en"): LocalResponse {
        val isGerman = language.startsWith("de")
        val entry = if (isGerman) FALLBACK_DE.random() else FALLBACK_EN.random()
        Log.d(TAG, "[FALLBACK] '${entry.speech}' action=${entry.action}")
        return LocalResponse(entry.speech, entry.emotion, entry.action)
    }

    /**
     * Word-boundary match: checks if phrase appears as whole word(s) in text.
     */
    private fun containsPhrase(text: String, phrase: String): Boolean {
        return text == phrase || text.startsWith("$phrase ") ||
            text.endsWith(" $phrase") || " $phrase " in text
    }

    /**
     * Check if ANY word/phrase from the list matches as whole words in text.
     */
    private fun matchesAny(text: String, words: List<String>): Boolean {
        return words.any { containsPhrase(text, it) }
    }

    fun generateResponse(text: String, language: String = "en"): LocalResponse {
        // Strip punctuation so "Hands up!" and "Tanz!" match the same as "hands up" / "tanz"
        val lower = text.lowercase().trim()
            .replace(Regex("[!?.,;:…\"'()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val isGerman = language.startsWith("de")

        // Check stop phrases
        if (isStopPhrase(lower)) {
            val goodbye = if (isGerman) "Tsch\u00fcss! Es war sch\u00f6n mit dir zu sprechen."
                          else "Goodbye! It was nice talking with you."
            return LocalResponse(goodbye, "happy", "wave", keepSession = false, conversationEnd = true)
        }

        // Empty input
        if (lower.isBlank() || lower.length < 2) {
            return LocalResponse("", "neutral", "idle")
        }

        // ═══════════════════════════════════════════════════════════════
        // ACTION COMMANDS — word-boundary matching (tight, no false positives)
        // ═══════════════════════════════════════════════════════════════

        // Dance
        if (matchesAny(lower, DANCE_WORDS)) {
            Log.d(TAG, "[ACTION] Dance detected in: '$lower'")
            val responses = if (isGerman) listOf("Yeehaw! Tanzzeit!", "Lass uns grooven!", "Schau mir zu!")
                            else listOf("Woohoo! Dance time!", "Let's groove!", "Watch me move!")
            return LocalResponse(responses.random(), "excited", "dance")
        }

        // Wave
        if (matchesAny(lower, WAVE_WORDS)) {
            Log.d(TAG, "[ACTION] Wave detected in: '$lower'")
            val responses = if (isGerman) listOf("Hey hey hey!", "Hallo Freund!", "Gr\u00fc\u00df dich!")
                            else listOf("Hey there, friend!", "Hi hi hi!", "Hello hello!")
            return LocalResponse(responses.random(), "happy", "wave")
        }

        // Hands up
        if (matchesAny(lower, HANDS_UP_WORDS)) {
            Log.d(TAG, "[ACTION] Hands up detected in: '$lower'")
            val responses = if (isGerman)
                listOf("H\u00e4nde hoch, nicht schie\u00dfen!", "Juhu! H\u00e4nde hoch!", "Yeah! So macht man das!")
            else listOf("Hands up, don't shoot!", "Woohoo! Hands up!", "Yeah! That's how we do it!")
            return LocalResponse(responses.random(), "excited", "hands_up")
        }

        // Clap
        if (matchesAny(lower, CLAP_WORDS)) {
            Log.d(TAG, "[ACTION] Clap detected in: '$lower'")
            val responses = if (isGerman) listOf("Bravo! Bravo!", "Applaus!")
                            else listOf("Awesome! Clap clap!", "Give it up!")
            return LocalResponse(responses.random(), "excited", "clap")
        }

        // Bow
        if (matchesAny(lower, BOW_WORDS)) {
            Log.d(TAG, "[ACTION] Bow detected in: '$lower'")
            val responses = if (isGerman) listOf("Zu Ihren Diensten!", "Es ist mir eine Ehre!")
                            else listOf("At your service!", "The pleasure is mine!")
            return LocalResponse(responses.random(), "happy", "bow")
        }

        // ═══════════════════════════════════════════════════════════════
        // INFORMATION QUERIES — only exact phrase matches
        // ═══════════════════════════════════════════════════════════════

        // Time — use multi-word phrases to avoid false positives
        if ("what time" in lower || "the time" in lower || "wie sp\u00e4t" in lower ||
            "wie viel uhr" in lower || "wieviel uhr" in lower || containsPhrase(lower, "uhrzeit")) {
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val speech = if (isGerman) "Es ist $t Uhr." else "It's $t."
            return LocalResponse(speech, "neutral", "nod")
        }

        // ═══════════════════════════════════════════════════════════════
        // CONVERSATION — only very specific phrase matches
        // Everything else goes to OpenAI for a smart answer
        // ═══════════════════════════════════════════════════════════════

        // How are you (exact phrases only)
        if ("how are you" in lower || "wie geht" in lower || "how do you feel" in lower) {
            val responses = if (isGerman) listOf("Mir geht's fantastisch!", "Super duper! Willst du tanzen?", "Ich f\u00fchl mich toll!")
                            else listOf("I'm fantastic! Ready to party!", "Super duper! Wanna dance?", "Feeling awesome today!")
            return LocalResponse(responses.random(), "excited", "wave")
        }

        // Who are you / your name (exact phrases only)
        if ("your name" in lower || "what's your name" in lower || "who are you" in lower ||
            "wer bist du" in lower || "wie hei\u00dft du" in lower || "wie heisst du" in lower) {
            val responses = if (isGerman) listOf("Ich bin Alpha Mini, dein Roboter-Kumpel!", "Nenn mich Alpha Mini!", "Alpha Mini zu deinen Diensten!")
                            else listOf("I'm Alpha Mini, your robot buddy!", "Call me Alpha Mini! Nice to meet you!", "Alpha Mini at your service!")
            return LocalResponse(responses.random(), "happy", "wave")
        }

        // ═══════════════════════════════════════════════════════════════
        // DEFAULT — return empty so orchestrator can try OpenAI LLM
        // ═══════════════════════════════════════════════════════════════
        Log.d(TAG, "[UNRECOGNIZED] '$lower' - no rule matched, returning empty for LLM fallback")
        return LocalResponse("", "neutral", "idle")
    }

    /**
     * Generate a fun "I don't know" response as ultimate fallback
     * (used when both rule-based AND OpenAI fail).
     */
    fun generateUnrecognizedResponse(language: String = "en"): LocalResponse {
        val isGerman = language.startsWith("de")
        val responses = if (isGerman)
            listOf(
                "Hmm, das wei\u00df ich leider nicht. Aber ich kann tanzen! Sag einfach Tanz!",
                "Gute Frage! Ich bin nur ein kleiner Roboter. Frag mich lieber nach der Uhrzeit!",
                "Das \u00fcbersteigt meine Roboter-Gehirnkapazit\u00e4t! Aber ich kann winken!",
                "Keine Ahnung, aber willst du mich tanzen sehen?",
                "Das ist eine schwierige Frage f\u00fcr einen Roboter. Sag Witz f\u00fcr einen Roboter-Witz!"
            )
        else
            listOf(
                "Hmm, I don't know that one. But I can dance! Just say dance!",
                "Good question! I'm just a little robot. Ask me what time it is!",
                "That's beyond my robot brain! But I can wave at you!",
                "No idea, but wanna see me dance?",
                "Tough question for a robot. Say joke for a robot joke!"
            )
        Log.d(TAG, "[UNRECOGNIZED FALLBACK] '${responses.first().take(40)}'")
        return LocalResponse(responses.random(), "thinking", "think")
    }

    /**
     * Convert a LocalResponse into an LLMResponse that the orchestrator already understands.
     */
    fun toLLMResponse(local: LocalResponse, transcription: String?, language: String = "en-US"): LLMResponse {
        val emotionData = EMOTION_MAP[local.emotion] ?: EMOTION_MAP["neutral"]!!
        val finalAction = ACTION_OVERRIDE_MAP[local.action] ?: emotionData.action

        return LLMResponse(
            speech = local.speech,
            emotion = LLMResponse.Emotion.fromString(emotionData.chinese),
            action = finalAction,
            expression = emotionData.expression,
            light = emotionData.light,
            earcon = LLMResponse.Earcon.NONE,
            allowBargeIn = true,
            keepSession = local.keepSession,
            conversationEnd = local.conversationEnd,
            askFollowup = local.askFollowup,
            timing = LLMResponse.ResponseTiming(preRollMs = 50, postRollMs = 100),
            transcription = transcription,
            language = language
        )
    }
}
