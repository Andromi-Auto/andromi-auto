package com.andrerinas.openheadunit.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Andromi Auto's one-time root setup for automatic Self Mode.
 *
 * Android Auto 17.4+ disabled its WirelessStartupReceiver, which is what lets an app start a
 * wireless (here: loopback) session without the developer "headunit server". Re-enabling it with
 * root restores that route, so Self Mode can start on its own, for example when the car's
 * Bluetooth connects. The same root shell also lets the app open itself from the background
 * (display over other apps) and read which Bluetooth device connected.
 *
 * Whether the receiver is enabled can be read without root, so a user who ran the command by
 * hand (a root terminal, or adb as root) is detected the same way.
 */
object RootSetup {

    const val AA_PACKAGE = "com.google.android.projection.gearhead"
    const val RECEIVER =
        "com.google.android.apps.auto.wireless.setup.receiver.WirelessStartupReceiver"

    /** The command from Open Headunit's README, for users who run it themselves. */
    const val RECEIVER_COMMAND = "pm enable --user 0 $AA_PACKAGE/$RECEIVER"

    enum class State { NO_ANDROID_AUTO, RECEIVER_MISSING, RECEIVER_DISABLED, READY }

    /** Reads the receiver's state from the package manager; needs no root. */
    fun state(context: Context): State {
        val pm = context.packageManager
        try {
            pm.getPackageInfo(AA_PACKAGE, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return State.NO_ANDROID_AUTO
        }
        val component = ComponentName(AA_PACKAGE, RECEIVER)
        return try {
            when (pm.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> State.READY
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> {
                    // Default means "as declared in Android Auto's manifest".
                    @Suppress("DEPRECATION")
                    val flags = if (Build.VERSION.SDK_INT >= 24) PackageManager.MATCH_DISABLED_COMPONENTS
                        else PackageManager.GET_DISABLED_COMPONENTS
                    if (pm.getReceiverInfo(component, flags).enabled) State.READY else State.RECEIVER_DISABLED
                }
                else -> State.RECEIVER_DISABLED
            }
        } catch (e: Exception) {
            // IllegalArgumentException / NameNotFoundException: this Android Auto has no such receiver.
            AppLog.w("RootSetup: receiver state unreadable: ${e.message}")
            State.RECEIVER_MISSING
        }
    }

    fun isReady(context: Context): Boolean = state(context) == State.READY

    /** The shell commands the setup runs as root, in order. */
    fun commands(context: Context): List<String> {
        val pkg = context.packageName
        val list = mutableListOf(RECEIVER_COMMAND)
        // Start the app from the Bluetooth broadcast (Android 10+ blocks background starts otherwise).
        list.add("appops set --user 0 $pkg SYSTEM_ALERT_WINDOW allow")
        if (Build.VERSION.SDK_INT >= 31) list.add("pm grant $pkg android.permission.BLUETOOTH_CONNECT")
        if (Build.VERSION.SDK_INT >= 33) list.add("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        return list
    }

    sealed class Result {
        object Done : Result()
        object NoRoot : Result()
        data class Failed(val message: String) : Result()
    }

    /**
     * Asks for root (Magisk / KernelSU show their prompt here) and runs [commands].
     * The receiver command is the one that matters; the permission commands are best effort.
     */
    suspend fun run(context: Context): Result = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (!shell.isRoot) return@withContext Result.NoRoot
            val cmds = commands(context)
            val first = Shell.cmd(cmds.first()).exec()
            AppLog.i("RootSetup: ${cmds.first()} -> ${first.code} ${first.out.joinToString(" ")} ${first.err.joinToString(" ")}")
            cmds.drop(1).forEach { c ->
                val r = Shell.cmd(c).exec()
                AppLog.i("RootSetup: $c -> ${r.code}")
            }
            if (isReady(context)) Result.Done
            else Result.Failed((first.err + first.out).joinToString("\n").ifBlank { "exit code ${first.code}" })
        } catch (e: Exception) {
            AppLog.w("RootSetup: root shell failed: ${e.message}")
            Result.NoRoot
        }
    }
}
