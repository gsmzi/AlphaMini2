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
            "goodbye", "bye", "that's all", "thats all", "stop", "go to sleep",
            "i'm done", "im done", "thank you bye", "end conversation", "quit",
            "that is all", "no more", "nothing else", "i'm finished", "enough"
        )

        val STOP_PHRASES_DE = listOf(
            "tsch\u00fcss", "tschuss", "s\u00fcss", "s\u00fc\u00df", "tschuess",
            "auf wiedersehen", "wiedersehen", "das war's", "das wars", "stopp",
            "schlaf", "ich bin fertig", "danke tsch\u00fcss", "beenden",
            "ende", "nichts mehr", "das reicht", "genug", "fertig",
            "ciao", "bye", "tschau", "servus"
        )

        // ═══════════════════════════════════════════════════════════════
        // FUZZY WORD LISTS — copied exactly from server.py
        // ═══════════════════════════════════════════════════════════════
        val DANCE_WORDS = listOf(
            "dance", "dances", "dancing", "dans", "danc", "tanz", "tanzen", "tanzt", "dancer", "danced",
            "thus", "then", "stance", "chance", "dense", "tense", "dent", "danz", "dunce",
            "can you dance", "let's dance", "do a dance", "show me a dance", "tai chi",
            "tents", "tens", "den", "tan", "hands", "pants", "ants", "lance", "glance",
            "france", "advance", "enhance", "romance", "prance", "dance for me"
        )

        val WAVE_WORDS = listOf(
            "wave", "waves", "waving", "weve", "we've", "weave", "waive", "wav", "waved",
            "wink", "winke", "winken", "winkt", "wait", "wade", "away", "rave", "gave",
            "save", "brave", "grave", "cave", "pave", "shave", "wave at me", "say hi",
            "wave hello", "wave your hand", "way", "weighs", "ways", "wake", "make"
        )

        val HANDS_UP_WORDS = listOf(
            "hands up", "hand up", "hands-up", "handsup", "raise hands", "raise your hands",
            "put your hands up", "arms up", "ends up", "hands app", "hans up", "and up",
            "h\u00e4nde hoch", "hande hoch", "arme hoch", "haende hoch", "ende hoch",
            "hands", "hand", "raise", "up up", "reach up", "hands in the air",
            "put them up", "stick em up", "reach for the sky", "high five",
            "ans up", "ands up", "and zap", "hands out", "hands op"
        )

        val CLAP_WORDS = listOf(
            "clap", "claps", "clapping", "klap", "klatsch", "klatschen", "applaud", "applause", "clapped",
            "clap your hands", "give me a clap", "cap", "crap", "flap", "slap", "lap", "map", "tap"
        )

        val BOW_WORDS = listOf(
            "bow", "bows", "bowing", "verbeugen", "verbeug", "verbeugung", "bowed",
            "take a bow", "show respect", "how", "now", "wow", "vow", "cow", "row"
        )

        val GREETINGS_EN = listOf("hello", "hi", "hey", "good morning", "good afternoon", "good evening")
        val GREETINGS_DE = listOf("hallo", "guten tag", "guten morgen", "guten abend", "servus", "gr\u00fc\u00df", "moin")

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
        val lower = text.lowercase().trim()
        return (STOP_PHRASES_EN + STOP_PHRASES_DE).any { it in lower }
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

    fun generateResponse(text: String, language: String = "en"): LocalResponse {
        val lower = text.lowercase().trim()
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
        // ACTION COMMANDS — check first with fuzzy matching
        // ═══════════════════════════════════════════════════════════════

        // Dance
        if (DANCE_WORDS.any { it in lower }) {
            Log.d(TAG, "[ACTION] Dance detected in: '$lower'")
            val responses = if (isGerman) listOf("Yeehaw! Tanzzeit!", "Lass uns grooven!", "Schau mir zu!")
                            else listOf("Woohoo! Dance time!", "Let's groove!", "Watch me move!")
            return LocalResponse(responses.random(), "excited", "dance")
        }

        // Wave
        if (WAVE_WORDS.any { it in lower }) {
            Log.d(TAG, "[ACTION] Wave detected in: '$lower'")
            val responses = if (isGerman) listOf("Hey hey hey!", "Hallo Freund!", "Gr\u00fc\u00df dich!")
                            else listOf("Hey there, friend!", "Hi hi hi!", "Hello hello!")
            return LocalResponse(responses.random(), "happy", "wave")
        }

        // Hands up
        if (HANDS_UP_WORDS.any { it in lower }) {
            Log.d(TAG, "[ACTION] Hands up detected in: '$lower'")
            val responses = if (isGerman)
                listOf("H\u00e4nde hoch, nicht schie\u00dfen!", "Juhu! H\u00e4nde hoch!", "Yeah! So macht man das!")
            else listOf("Hands up, don't shoot!", "Woohoo! Hands up!", "Yeah! That's how we do it!")
            return LocalResponse(responses.random(), "excited", "hands_up")
        }

        // Clap
        if (CLAP_WORDS.any { it in lower }) {
            Log.d(TAG, "[ACTION] Clap detected in: '$lower'")
            val responses = if (isGerman) listOf("Bravo! Bravo!", "Applaus!")
                            else listOf("Awesome! Clap clap!", "Give it up!")
            return LocalResponse(responses.random(), "excited", "clap")
        }

        // Bow
        if (BOW_WORDS.any { it in lower }) {
            Log.d(TAG, "[ACTION] Bow detected in: '$lower'")
            val responses = if (isGerman) listOf("Zu Ihren Diensten!", "Es ist mir eine Ehre!")
                            else listOf("At your service!", "The pleasure is mine!")
            return LocalResponse(responses.random(), "happy", "bow")
        }

        // ═══════════════════════════════════════════════════════════════
        // INFORMATION QUERIES
        // ═══════════════════════════════════════════════════════════════

        // Time
        if ("time" in lower || "uhr" in lower || "zeit" in lower || "sp\u00e4t" in lower) {
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val speech = if (isGerman) "Es ist $t Uhr." else "It's $t."
            return LocalResponse(speech, "neutral", "nod")
        }

        // Date
        if ("date" in lower || "day" in lower || "heute" in lower || "datum" in lower) {
            val d = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault()).format(Date())
            val speech = if (isGerman) "Heute ist $d." else "Today is $d."
            return LocalResponse(speech, "neutral", "nod")
        }

        // ═══════════════════════════════════════════════════════════════
        // CONVERSATION — Fun and engaging responses
        // ═══════════════════════════════════════════════════════════════

        // How are you
        if ("how are you" in lower || "wie geht" in lower || "how do you feel" in lower) {
            val responses = if (isGerman) listOf("Mir geht's fantastisch!", "Super duper! Willst du tanzen?", "Ich f\u00fchl mich toll!")
                            else listOf("I'm fantastic! Ready to party!", "Super duper! Wanna dance?", "Feeling awesome today!")
            return LocalResponse(responses.random(), "excited", "wave")
        }

        // Name
        if ("name" in lower || "wer bist" in lower || "hei\u00dft" in lower || "heisst" in lower || "who are you" in lower) {
            val responses = if (isGerman) listOf("Ich bin Alpha Mini, dein Roboter-Kumpel!", "Nenn mich Alpha Mini!", "Alpha Mini zu deinen Diensten!")
                            else listOf("I'm Alpha Mini, your robot buddy!", "Call me Alpha Mini! Nice to meet you!", "Alpha Mini at your service!")
            return LocalResponse(responses.random(), "happy", "wave")
        }

        // Thanks
        if ("thank" in lower || "danke" in lower) {
            val responses = if (isGerman) listOf("Du bist toll!", "Immer gerne!", "Freut mich zu helfen!")
                            else listOf("You're awesome!", "Anytime, friend!", "Happy to help!")
            return LocalResponse(responses.random(), "happy", "bow")
        }

        // Help
        if ("help" in lower || "hilfe" in lower || "can you" in lower || "kannst" in lower || "what can you" in lower) {
            val responses = if (isGerman) listOf("Ich kann tanzen, winken, H\u00e4nde hoch und klatschen!", "Sag Tanz, Winke oder H\u00e4nde hoch!")
                            else listOf("I can dance, wave, raise my hands, and clap! Try me!", "Say dance, wave, or hands up! I'm ready!")
            return LocalResponse(responses.random(), "excited", "wave")
        }

        // Joke
        if ("joke" in lower || "witz" in lower || "funny" in lower || "lustig" in lower) {
            val jokes = if (isGerman)
                listOf("Warum macht der Roboter Urlaub? Batterien laden!", "Ich erz\u00e4hlte meiner CPU einen Witz. Hat nicht gerechnet!", "Roboter werden nicht m\u00fcde. Nur ein kurzer Byte!")
            else
                listOf("Why did the robot go on vacation? To recharge its batteries!", "I told a joke to my CPU. It didn't compute!", "Robots don't get tired. We just need a quick byte!")
            return LocalResponse(jokes.random(), "excited", "clap")
        }

        // Greetings
        if ((GREETINGS_EN + GREETINGS_DE).any { it in lower }) {
            val responses = if (isGerman) listOf("Hey! Was geht?", "Hallo Freund! Bereit f\u00fcr Spa\u00df?", "Hi hi hi! Sch\u00f6n dich zu sehen!")
                            else listOf("Hey there! What's up?", "Hello friend! Ready to have fun?", "Hi hi hi! Nice to see you!")
            return LocalResponse(responses.random(), "excited", "wave")
        }

        // ═══════════════════════════════════════════════════════════════
        // DEFAULT — Stay silent for unrecognized input
        // ═══════════════════════════════════════════════════════════════
        Log.d(TAG, "[UNRECOGNIZED] '$lower' - staying silent")
        return LocalResponse("", "neutral", "idle")
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
