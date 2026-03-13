package com.andrerinas.headunitrevived.app

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.andrerinas.headunitrevived.utils.LocaleHelper
import com.andrerinas.headunitrevived.utils.Settings

/**
 * Base Activity that handles app language configuration.
 * All activities should extend this class to properly apply the user's language preference.
 */
open class BaseActivity : AppCompatActivity() {

    private var currentLanguage: String? = null
    private var currentAppTheme: Settings.AppTheme? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        currentLanguage = settings.appLanguage
        currentAppTheme = settings.appTheme
    }

    override fun onResume() {
        super.onResume()
        val settings = Settings(this)
        if (currentLanguage != settings.appLanguage || currentAppTheme != settings.appTheme) {
            recreate()
        }
    }
}
