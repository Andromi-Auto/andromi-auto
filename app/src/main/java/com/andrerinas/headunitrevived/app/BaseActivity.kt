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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLanguage = Settings(this).appLanguage
    }

    override fun onResume() {
        super.onResume()
        val settings = Settings(this)
        if (currentLanguage != settings.appLanguage) {
            // Language changed while we were in the background
            recreate()
        }
    }
}
