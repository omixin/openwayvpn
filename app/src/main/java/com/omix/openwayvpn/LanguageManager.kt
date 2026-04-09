package com.omix.openwayvpn

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Управление языком приложения.
 * Сохраняет выбор в SharedPreferences и применяет Locale.
 */
object LanguageManager {
    private const val PREFS = "openway_settings"
    private const val KEY_LANGUAGE = "app_language"
    const val LANG_AUTO = "auto"
    const val LANG_EN = "en"
    const val LANG_RU = "ru"

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANG_AUTO) ?: LANG_AUTO
    }

    fun setLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE, lang)
            .apply()
    }

    fun applyLocale(context: Context): Context {
        val lang = getLanguage(context)
        val locale = when (lang) {
            LANG_EN -> Locale.ENGLISH
            LANG_RU -> Locale("ru")
            else -> Locale.getDefault()
        }
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    fun restartActivity(context: Context) {
        (context as? Activity)?.recreate()
    }
}
