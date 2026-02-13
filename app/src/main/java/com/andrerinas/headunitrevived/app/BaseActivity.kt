package com.andrerinas.headunitrevived.app

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.andrerinas.headunitrevived.utils.LocaleHelper

/**
 * Base Activity that handles app language configuration.
 * All activities should extend this class to properly apply the user's language preference.
 */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }
}
