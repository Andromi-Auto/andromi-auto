package com.andrerinas.headunitrevived.utils

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.andrerinas.headunitrevived.BuildConfig
import com.andrerinas.headunitrevived.IShizuku
import java.io.DataOutputStream
import rikka.shizuku.Shizuku

class SUExecutor {

    private val all: Collection<SUImplementation> = buildList {
        add(RootImpl())

        // Shizuku min sdk
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            add(ShizukuImpl())
    }

    private var registered: Boolean = false
    private var active: SUImplementation? = null
    private var checkedPermissionOnBoot = false

    fun register() {
        if (registered)
            return

        registered = true
        all.forEach { it.register() }
    }

    fun unregister() {
        if (!registered)
            return

        registered = false
        all.forEach { it.unregister() }
    }

    // don't keep nagging if multiple services request on boot
    fun checkPermissionOnBoot() {
        if (checkedPermissionOnBoot)
            return

        checkPermission()
        checkedPermissionOnBoot = true
    }

    fun checkPermission(): Boolean {
        testRegistered()

        // check if on main thread (otherwise permission request dialog doesn't appear)
        if (Looper.myLooper() != Looper.getMainLooper())
            throw IllegalStateException("#checkPermission must be called on main thread")

        for (impl in all) {
            if (!impl.checkPermission())
                continue

            // permission granted!
            Log.i("SUExecutor", "SU granted by ${impl.name}")
            active = impl
            return true
        }

        // nobody granted
        Log.i("SUExecutor", "SU not granted")
        active = null
        return false
    }

    fun setProp(key: String, value: String): Boolean {
        testRegistered()

        if (active == null) {
            Log.w("SUExecutor", "#setProp failed: Not active")
            return false
        }

        val exitCode = active?.runShell("setprop $key $value") ?: -1
        return exitCode == 0
    }

    private fun testRegistered() {
        if (!registered)
            throw IllegalStateException("SUExecutor not registered")
    }


    private interface SUImplementation {

        val name: String

        fun register()

        fun unregister()

        fun checkPermission(): Boolean

        fun runShell(cmd: String): Int
    }

    private class RootImpl : SUImplementation {

        override val name = "Root"

        override fun register() {
        }

        override fun unregister() {
        }

        override fun checkPermission(): Boolean {
            return try {
                val p = ProcessBuilder("su", "-c", "id").start()

                p.waitFor() == 0
            } catch (_: Exception) {
                false
            }
        }

        override fun runShell(cmd: String): Int {
            return try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                os.writeBytes("$cmd\n")
                os.writeBytes("exit\n")
                os.flush()

                process.waitFor()
            } catch (e: Exception) {
                Log.e("SUExecutor", "#runShell failed", e)
                -1
            }
        }
    }

    private class ShizukuImpl : SUImplementation, Shizuku.OnRequestPermissionResultListener {

        override val name = "Shizuku"

        var hasPermission: Boolean = false
        val connection = ShizukuServiceConnection()

        override fun register() {
            Shizuku.addRequestPermissionResultListener(this)
        }

        override fun unregister() {
            Shizuku.removeRequestPermissionResultListener(this)
        }

        override fun checkPermission(): Boolean {
            return this.hasPermission
        }

        override fun runShell(cmd: String): Int {
            return try {
                if (this.connection.service == null) {
                    Log.w("SUExecutor", "#runShell failed: Shizuku service not connected")
                    return -1
                }

                this.connection.service!!.execShell(cmd)
            } catch (e: Exception) {
                Log.e("SUExecutor", "#runShell failed", e)
                -1
            }
        }

        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            this.hasPermission = run {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

                granted && !Shizuku.isPreV11()
            }

            if (this.hasPermission)
                this.connection.bind()
            else
                this.connection.unbind()
        }


        private class PrivilegedService : IShizuku.Stub() {

            override fun execShell(command: String): Int {
                return Runtime.getRuntime().exec(arrayOf("sh", "-c", command)).waitFor()
            }
        }

        private inner class ShizukuServiceConnection : ServiceConnection {

            private val args by lazy {
                Shizuku.UserServiceArgs(
                    ComponentName(
                        BuildConfig.APPLICATION_ID,
                        PrivilegedService::class.java.name,
                    ),
                )
                    .daemon(false)
                    .processNameSuffix("privileged_service")
            }

            private var isBound: Boolean = false
            var service: IShizuku? = null

            override fun onServiceConnected(p0: ComponentName?, service: IBinder?) {
                this.service = IShizuku.Stub.asInterface(service)
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                this.service = null
            }

            fun bind() {
                if (isBound || !hasPermission)
                    return

                isBound = true
                Shizuku.bindUserService(args, this)
            }

            fun unbind() {
                if (!isBound)
                    return

                isBound = false
                service = null
                Shizuku.unbindUserService(args, this, true)
            }
        }
    }
}
