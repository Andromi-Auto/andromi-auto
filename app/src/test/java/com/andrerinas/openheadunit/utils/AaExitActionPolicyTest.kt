package com.andrerinas.openheadunit.utils

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class AaExitActionPolicyTest {

    @Test
    fun exitActionMappingResolvesKnownValues() {
        assertEquals(Settings.ExitAction.OEM_LAUNCHER, Settings.ExitAction.fromInt(0))
        assertEquals(Settings.ExitAction.APP_HOME, Settings.ExitAction.fromInt(1))
        assertEquals(Settings.ExitAction.DISCONNECT, Settings.ExitAction.fromInt(2))
    }

    @Test
    fun exitActionMappingFallsBackToOemLauncherOnInvalidValues() {
        assertEquals(Settings.ExitAction.OEM_LAUNCHER, Settings.ExitAction.fromInt(-1))
        assertEquals(Settings.ExitAction.OEM_LAUNCHER, Settings.ExitAction.fromInt(3))
        assertEquals(Settings.ExitAction.OEM_LAUNCHER, Settings.ExitAction.fromInt(999))
    }

    @Test
    fun exitActionDefaultIsOemLauncherWhenPrefMissing() {
        val mockContext = mock(Context::class.java)
        val mockPrefs = mock(SharedPreferences::class.java)

        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        `when`(mockPrefs.getInt(eq("aa-exit-action"), eq(Settings.ExitAction.OEM_LAUNCHER.value)))
            .thenReturn(Settings.ExitAction.OEM_LAUNCHER.value)

        val settings = Settings(mockContext)
        assertEquals(Settings.ExitAction.OEM_LAUNCHER, settings.aaExitAction)
    }

    @Test
    fun exitActionKeyIsRegisteredInBackupManager() {
        val registeredType = SettingsBackupManager.backupKeys["aa-exit-action"]
        assertNotNull("aa-exit-action must be registered in SettingsBackupManager", registeredType)
        assertEquals(SettingsBackupManager.ValueType.INT, registeredType)
    }
}
