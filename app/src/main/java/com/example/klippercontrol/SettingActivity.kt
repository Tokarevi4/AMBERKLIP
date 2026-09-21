package com.example.klippercontrol

import android.app.Activity
import android.os.Bundle
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup

class SettingsActivity : Activity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(
            LocaleHelper.apply(newBase)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionBar?.hide()

        setContentView(R.layout.activity_settings)

        val backButton =
            findViewById<ImageButton>(R.id.backButton)

        val languageGroup =
            findViewById<RadioGroup>(R.id.languageGroup)

        val languageSystem =
            findViewById<RadioButton>(R.id.languageSystem)

        val languageRussian =
            findViewById<RadioButton>(R.id.languageRussian)

        val languageEnglish =
            findViewById<RadioButton>(R.id.languageEnglish)

        backButton.setOnClickListener {
            finish()
        }

        when (LocaleHelper.getLanguage(this)) {

            LocaleHelper.LANGUAGE_RUSSIAN -> {
                languageRussian.isChecked = true
            }

            LocaleHelper.LANGUAGE_ENGLISH -> {
                languageEnglish.isChecked = true
            }

            else -> {
                languageSystem.isChecked = true
            }
        }

        languageGroup.setOnCheckedChangeListener {
                _, checkedId ->

            val language = when (checkedId) {

                R.id.languageRussian ->
                    LocaleHelper.LANGUAGE_RUSSIAN

                R.id.languageEnglish ->
                    LocaleHelper.LANGUAGE_ENGLISH

                R.id.languageSystem ->
                    LocaleHelper.LANGUAGE_SYSTEM

                else ->
                    return@setOnCheckedChangeListener
            }

            LocaleHelper.setLanguage(
                this,
                language
            )

            setResult(RESULT_OK)
            finish()
        }
    }
}
