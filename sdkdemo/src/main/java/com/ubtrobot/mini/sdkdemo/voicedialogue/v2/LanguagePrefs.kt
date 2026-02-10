package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.content.Context
import android.util.Log

/**
 * Persists the selected language mode across reboots.
 * Default: German (DE).
 */
object LanguagePrefs {
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "language_mode"
    private const val TAG = "LanguagePrefs"

    fun get(context: Context): DialogueConfig.Language {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_LANGUAGE, "DE") ?: "DE"
        return if (stored == "EN") DialogueConfig.Language.EN else DialogueConfig.Language.DE
    }

    fun set(context: Context, language: DialogueConfig.Language) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language.name).apply()
        Log.d(TAG, "Language persisted: ${language.name}")
    }
}
