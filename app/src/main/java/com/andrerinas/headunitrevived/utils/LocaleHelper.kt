package com.andrerinas.headunitrevived.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    const val SYSTEM_DEFAULT = ""

    /**
     * Gets available locales from the app's resources.
     * This dynamically detects all values-XX directories in the APK.
     */
    fun getAvailableLocales(context: Context): List<Locale> {
        val assetLocales = context.assets.locales
        return assetLocales
            .filter { it.isNotEmpty() }
            .mapNotNull { parseLocale(it) }
            .distinctBy { it.toString() }
            .sortedBy { it.getDisplayName(it) }
    }

    /**
     * Parse a locale string like "cs", "pt-rBR", "zh-rTW" into a Locale object.
     */
    private fun parseLocale(localeString: String): Locale? {
        // Android resource locales use format like "pt-rBR" for regional variants
        val normalized = localeString.replace("-r", "-")
        val parts = normalized.split("-", "_")
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            3 -> Locale(parts[0], parts[1], parts[2])
            else -> null
        }
    }

    /**
     * Converts a Locale to a storage string format.
     */
    fun localeToString(locale: Locale?): String {
        if (locale == null) return SYSTEM_DEFAULT
        return if (locale.country.isNotEmpty()) {
            "${locale.language}-${locale.country}"
        } else {
            locale.language
        }
    }

    /**
     * Converts a stored string back to a Locale.
     */
    fun stringToLocale(localeString: String): Locale? {
        if (localeString.isEmpty()) return null
        return parseLocale(localeString)
    }

    /**
     * Gets the display name for a locale in its own language.
     */
    fun getDisplayName(locale: Locale): String {
        val displayName = locale.getDisplayName(locale)
        // Capitalize first letter
        return displayName.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
    }

    /**
     * Applies the selected locale to a context.
     * Returns a new Context with the applied locale.
     */
    fun applyLocale(context: Context, settings: Settings): Context {
        val localeString = settings.appLanguage
        if (localeString.isEmpty()) {
            // Use system default
            return context
        }

        val locale = stringToLocale(localeString) ?: return context
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLocales(android.os.LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    /**
     * Updates the configuration for the given context.
     * Use this in attachBaseContext of Activities.
     */
    fun wrapContext(context: Context): Context {
        val settings = Settings(context)
        return applyLocale(context, settings)
    }
}
