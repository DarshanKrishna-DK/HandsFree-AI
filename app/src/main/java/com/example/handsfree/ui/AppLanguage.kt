package com.example.handsfree.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

object AppLanguage {
    fun code(context: Context): String {
        val language = if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) locales[0].language else context.resources.configuration.locales[0].language
        } else context.getSharedPreferences("conversation", 0).getString("app_language", null)
            ?: context.resources.configuration.locales[0].language
        return if (language.startsWith("kn")) "kn-IN" else "en-IN"
    }
    fun localized(context: Context): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(code(context))) })

    fun select(activity: Activity, code: String) {
        require(code in setOf("en-IN", "kn-IN"))
        activity.getSharedPreferences("conversation", 0).edit().putString("app_language", code).apply()
        if (Build.VERSION.SDK_INT >= 33)
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(code)
        else activity.recreate()
    }
}

fun Context.uiText(@StringRes id: Int, vararg args: Any): String = AppLanguage.localized(this).getString(id, *args)
