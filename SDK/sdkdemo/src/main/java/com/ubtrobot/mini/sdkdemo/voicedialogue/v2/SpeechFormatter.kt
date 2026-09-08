package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Speech Formatter
 *
 * Post-processes text for natural TTS output:
 * - Converts numbers to spoken form
 * - Formats times and dates
 * - Inserts pauses at punctuation
 * - Removes/replaces URLs
 * - Splits into sentences for incremental TTS
 */
class SpeechFormatter(
    private val language: DialogueConfig.Language = DialogueConfig.Language.EN
) {
    companion object {
        // Pause markers (SSML-style, may be TTS-specific)
        const val SHORT_PAUSE = "<break time=\"200ms\"/>"
        const val MEDIUM_PAUSE = "<break time=\"400ms\"/>"
        const val LONG_PAUSE = "<break time=\"600ms\"/>"

        // URL pattern
        private val URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
        )

        // Number patterns
        private val NUMBER_PATTERN = Pattern.compile("\\d+")
        private val TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})")
        private val DATE_PATTERN = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{2,4})")
        private val DECIMAL_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)")
        private val ORDINAL_PATTERN = Pattern.compile("(\\d+)(st|nd|rd|th)", Pattern.CASE_INSENSITIVE)

        // Sentence boundaries
        private val SENTENCE_PATTERN = Pattern.compile("(?<=[.!?])\\s+")
    }

    /**
     * Format text for TTS
     */
    fun format(text: String): String {
        var result = text

        // Step 1: Remove or replace URLs
        result = formatURLs(result)

        // Step 2: Format numbers (order matters!)
        result = formatOrdinals(result)
        result = formatTimes(result)
        result = formatDates(result)
        result = formatDecimals(result)
        result = formatNumbers(result)

        // Step 3: Insert pauses at punctuation
        result = insertPauses(result)

        // Step 4: Clean up whitespace
        result = result.replace(Regex("\\s+"), " ").trim()

        return result
    }

    /**
     * Split text into sentences for incremental TTS
     */
    fun splitIntoSentences(text: String): List<String> {
        return SENTENCE_PATTERN.split(text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Format for natural pauses (without SSML, for TTS that doesn't support it)
     */
    fun formatWithNaturalPauses(text: String): String {
        var result = format(text)

        // Remove SSML tags if TTS doesn't support them
        result = result.replace(Regex("<break[^>]*/>"), ", ")

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE FORMATTING METHODS
    // ═══════════════════════════════════════════════════════════════

    private fun formatURLs(text: String): String {
        return URL_PATTERN.matcher(text).replaceAll(
            if (language == DialogueConfig.Language.DE) "Link" else "link"
        )
    }

    private fun formatTimes(text: String): String {
        val matcher = TIME_PATTERN.matcher(text)
        val sb = StringBuffer()

        while (matcher.find()) {
            val hour = matcher.group(1)?.toIntOrNull() ?: 0
            val minute = matcher.group(2)?.toIntOrNull() ?: 0

            val spoken = if (language == DialogueConfig.Language.DE) {
                formatTimeGerman(hour, minute)
            } else {
                formatTimeEnglish(hour, minute)
            }
            matcher.appendReplacement(sb, spoken)
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun formatTimeEnglish(hour: Int, minute: Int): String {
        val period = if (hour < 12) "A M" else "P M"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }

        return if (minute == 0) {
            "$displayHour o'clock $period"
        } else {
            val minuteStr = if (minute < 10) "oh ${numberToWordsEnglish(minute)}"
            else numberToWordsEnglish(minute)
            "${numberToWordsEnglish(displayHour)} $minuteStr $period"
        }
    }

    private fun formatTimeGerman(hour: Int, minute: Int): String {
        return if (minute == 0) {
            "${numberToWordsGerman(hour)} Uhr"
        } else {
            "${numberToWordsGerman(hour)} Uhr ${numberToWordsGerman(minute)}"
        }
    }

    private fun formatDates(text: String): String {
        val matcher = DATE_PATTERN.matcher(text)
        val sb = StringBuffer()

        while (matcher.find()) {
            val day = matcher.group(1)?.toIntOrNull() ?: 1
            val month = matcher.group(2)?.toIntOrNull() ?: 1
            val year = matcher.group(3)?.toIntOrNull() ?: 2024

            val spoken = if (language == DialogueConfig.Language.DE) {
                formatDateGerman(day, month, year)
            } else {
                formatDateEnglish(day, month, year)
            }
            matcher.appendReplacement(sb, spoken)
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun formatDateEnglish(day: Int, month: Int, year: Int): String {
        val monthNames = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val monthName = monthNames.getOrElse(month - 1) { "month" }
        val ordinal = ordinalEnglish(day)
        return "$monthName $ordinal"
    }

    private fun formatDateGerman(day: Int, month: Int, year: Int): String {
        val monthNames = listOf(
            "Januar", "Februar", "März", "April", "Mai", "Juni",
            "Juli", "August", "September", "Oktober", "November", "Dezember"
        )
        val monthName = monthNames.getOrElse(month - 1) { "Monat" }
        return "${ordinalGerman(day)} $monthName"
    }

    private fun formatDecimals(text: String): String {
        val matcher = DECIMAL_PATTERN.matcher(text)
        val sb = StringBuffer()

        while (matcher.find()) {
            val whole = matcher.group(1)?.toIntOrNull() ?: 0
            val decimal = matcher.group(2) ?: ""

            val spoken = if (language == DialogueConfig.Language.DE) {
                "${numberToWordsGerman(whole)} Komma ${decimal.map { numberToWordsGerman(it.toString().toInt()) }.joinToString(" ")}"
            } else {
                "${numberToWordsEnglish(whole)} point ${decimal.map { numberToWordsEnglish(it.toString().toInt()) }.joinToString(" ")}"
            }
            matcher.appendReplacement(sb, spoken)
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun formatOrdinals(text: String): String {
        val matcher = ORDINAL_PATTERN.matcher(text)
        val sb = StringBuffer()

        while (matcher.find()) {
            val number = matcher.group(1)?.toIntOrNull() ?: 1
            val spoken = if (language == DialogueConfig.Language.DE) {
                ordinalGerman(number)
            } else {
                ordinalEnglish(number)
            }
            matcher.appendReplacement(sb, spoken)
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun formatNumbers(text: String): String {
        val matcher = NUMBER_PATTERN.matcher(text)
        val sb = StringBuffer()

        while (matcher.find()) {
            val number = matcher.group().toIntOrNull()
            if (number != null && number < 10000) {
                val spoken = if (language == DialogueConfig.Language.DE) {
                    numberToWordsGerman(number)
                } else {
                    numberToWordsEnglish(number)
                }
                matcher.appendReplacement(sb, spoken)
            }
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun insertPauses(text: String): String {
        var result = text

        // Long pause after sentences
        result = result.replace(". ", ". $MEDIUM_PAUSE ")
        result = result.replace("! ", "! $MEDIUM_PAUSE ")
        result = result.replace("? ", "? $MEDIUM_PAUSE ")

        // Short pause at commas
        result = result.replace(", ", ", $SHORT_PAUSE ")

        // Medium pause at colons and semicolons
        result = result.replace(": ", ": $SHORT_PAUSE ")
        result = result.replace("; ", "; $SHORT_PAUSE ")

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // NUMBER TO WORDS CONVERTERS
    // ═══════════════════════════════════════════════════════════════

    private fun numberToWordsEnglish(n: Int): String {
        if (n == 0) return "zero"

        val ones = listOf(
            "", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen"
        )
        val tens = listOf(
            "", "", "twenty", "thirty", "forty", "fifty",
            "sixty", "seventy", "eighty", "ninety"
        )

        return when {
            n < 20 -> ones[n]
            n < 100 -> "${tens[n / 10]}${if (n % 10 != 0) "-${ones[n % 10]}" else ""}"
            n < 1000 -> "${ones[n / 100]} hundred${if (n % 100 != 0) " and ${numberToWordsEnglish(n % 100)}" else ""}"
            else -> "${numberToWordsEnglish(n / 1000)} thousand${if (n % 1000 != 0) " ${numberToWordsEnglish(n % 1000)}" else ""}"
        }
    }

    private fun numberToWordsGerman(n: Int): String {
        if (n == 0) return "null"

        val ones = listOf(
            "", "eins", "zwei", "drei", "vier", "fünf",
            "sechs", "sieben", "acht", "neun", "zehn",
            "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn",
            "sechzehn", "siebzehn", "achtzehn", "neunzehn"
        )
        val tens = listOf(
            "", "", "zwanzig", "dreißig", "vierzig", "fünfzig",
            "sechzig", "siebzig", "achtzig", "neunzig"
        )

        return when {
            n < 20 -> ones[n]
            n < 100 -> {
                val unit = n % 10
                val ten = n / 10
                if (unit == 0) tens[ten]
                else "${if (unit == 1) "ein" else ones[unit]}und${tens[ten]}"
            }
            n < 1000 -> "${ones[n / 100]}hundert${if (n % 100 != 0) numberToWordsGerman(n % 100) else ""}"
            else -> "${numberToWordsGerman(n / 1000)}tausend${if (n % 1000 != 0) numberToWordsGerman(n % 1000) else ""}"
        }
    }

    private fun ordinalEnglish(n: Int): String {
        val suffix = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "${numberToWordsEnglish(n)}$suffix"
    }

    private fun ordinalGerman(n: Int): String {
        val suffix = if (n < 20) "ter" else "ster"
        return "${numberToWordsGerman(n)}$suffix"
    }
}

/**
 * Utility extension functions
 */
fun String.formatForSpeech(language: DialogueConfig.Language): String {
    return SpeechFormatter(language).format(this)
}

fun String.splitIntoSentences(): List<String> {
    return SpeechFormatter().splitIntoSentences(this)
}
