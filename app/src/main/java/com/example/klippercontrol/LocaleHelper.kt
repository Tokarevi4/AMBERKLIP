package com.example.klippercontrol

import android.content.Context
import android.content.res.Configuration
import android.os.Build
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
                getSystemLocale(context)
        }
    }

    private fun getSystemLocale(
        context: Context
    ): Locale {

        val configuration =
            context.resources.configuration

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            getLegacyLocale(configuration)
        }
    }

    @Suppress("DEPRECATION")
    private fun getLegacyLocale(
        configuration: Configuration
    ): Locale {
        return configuration.locale
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

        setConfigurationLocale(
            configuration,
            locale
        )

        return context.createConfigurationContext(
            configuration
        )
    }

    private fun setConfigurationLocale(
        configuration: Configuration,
        locale: Locale
    ) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
        } else {
            setLegacyConfigurationLocale(
                configuration,
                locale
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun setLegacyConfigurationLocale(
        configuration: Configuration,
        locale: Locale
    ) {
        configuration.locale = locale
    }
}
