package com.andrerinas.openheadunit.main

import android.content.Context
import com.andrerinas.openheadunit.App

/**
 * Andromi Auto's "Automatic start": when it is on, opening the app or the car's Bluetooth
 * connecting goes straight to Android Auto (Self Mode), without the start screen.
 */
object AndromiAutoStart {

    /** Whether opening the app should start Self Mode at once. */
    fun startsOnOpen(context: Context): Boolean {
        val app = App.provide(context)
        val settings = app.settings
        return settings.autoStartSelfMode &&
            settings.hasAcceptedDisclaimer &&
            settings.onboardingVersion >= OnboardingActivity.CURRENT_ONBOARDING_VERSION &&
            !app.commManager.isConnected
    }
}
