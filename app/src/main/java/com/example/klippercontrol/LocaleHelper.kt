package com.example.klippercontrol

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "amberklip_settings"
    private const val KEY_LANGUAGE = "language"

    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_RUSSIAN = "ru"
    const val LANGUAGE_ENGLISH = "en"

    fun getLanguage(context: Context): String {
        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_LANGUAGE,
                LANGUAGE_SYSTEM
            ) ?: LANGUAGE_SYSTEM
    }

    fun setLanguage(
        context: Context,
        language: String
    ) {
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_LANGUAGE,
                language
            )
            .apply()
    }

    private fun getSelectedLocale(
        context: Context
    ): Locale {

        return when (
            getLanguage(context)
        ) {

            LANGUAGE_RUSSIAN ->
                Locale("ru")

            LANGUAGE_ENGLISH ->
                Locale("en")

            else ->
                context.resources
                    .configuration
                    .locale
        }
    }

    fun apply(
        context: Context
    ): Context {

        val locale =
            getSelectedLocale(context)

        Locale.setDefault(locale)

        val configuration =
            Configuration(
                context.resources.configuration
            )

        configuration.locale =
            locale

        return context.createConfigurationContext(
            configuration
        )
    }

    fun applyToResources(
        context: Context
    ) {

        val locale =
            getSelectedLocale(context)

        Locale.setDefault(locale)

        val configuration =
            Configuration(
                context.resources.configuration
            )

        configuration.locale =
            locale

        context.resources.updateConfiguration(
            configuration,
            context.resources.displayMetrics
        )
    }
}
