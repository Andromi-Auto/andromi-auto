package com.andrerinas.openheadunit.main

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.app.BaseActivity
import com.andrerinas.openheadunit.utils.BluetoothHelper
import com.andrerinas.openheadunit.utils.RootSetup
import com.andrerinas.openheadunit.utils.Settings
import com.andrerinas.openheadunit.utils.ToastUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

/**
 * Andromi Auto: sets up "Automatic start". Three things make Android Auto start by itself when
 * the phone connects to the car: the root setup (Android Auto's startup receiver), the car's
 * Bluetooth device, and permission to open from the background. The switch turns it on or off.
 */
class AutoStartSetupActivity : BaseActivity() {

    private val settings: Settings get() = App.provide(this).settings
    private var rootRunning = false

    private val btPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) chooseBluetooth() else toast(R.string.andromi_bt_permission_needed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_start_setup)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val switch = findViewById<MaterialSwitch>(R.id.auto_switch)
        switch.isChecked = settings.autoStartSelfMode
        switch.setOnCheckedChangeListener { _, checked ->
            settings.autoStartSelfMode = checked
            render()
        }
        findViewById<MaterialButton>(R.id.root_button).setOnClickListener { runRootSetup() }
        findViewById<MaterialButton>(R.id.root_copy).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Andromi Auto", manualCommands()))
            toast(R.string.andromi_copied)
        }
        findViewById<MaterialButton>(R.id.bt_button).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else chooseBluetooth()
        }
        findViewById<MaterialButton>(R.id.overlay_button).setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val on = settings.autoStartSelfMode
        val rootState = RootSetup.state(this)
        val btName = bluetoothName()
        val overlayOk = canOpenFromBackground()

        findViewById<TextView>(R.id.auto_switch_summary).setText(
            when {
                !on -> R.string.andromi_auto_start_off
                rootState != RootSetup.State.READY || btName == null || !overlayOk -> R.string.andromi_auto_start_incomplete
                else -> R.string.andromi_auto_start_ready
            })

        // Root
        val rootStatus = findViewById<TextView>(R.id.root_status)
        val rootButton = findViewById<MaterialButton>(R.id.root_button)
        rootStatus.setText(when (rootState) {
            RootSetup.State.READY -> R.string.andromi_root_ready
            RootSetup.State.NO_ANDROID_AUTO -> R.string.andromi_root_no_aa
            RootSetup.State.RECEIVER_MISSING -> R.string.andromi_root_missing
            RootSetup.State.RECEIVER_DISABLED -> R.string.andromi_root_needed
        })
        rootButton.setText(if (rootState == RootSetup.State.READY) R.string.andromi_root_again else R.string.andromi_root_run)
        rootButton.isEnabled = !rootRunning && rootState != RootSetup.State.NO_ANDROID_AUTO
        setStatusIcon(R.id.root_icon, rootState == RootSetup.State.READY)
        findViewById<TextView>(R.id.root_command).text = manualCommands()

        // Bluetooth
        findViewById<TextView>(R.id.bt_status).text =
            btName?.let { getString(R.string.andromi_bt_chosen, it) } ?: getString(R.string.andromi_bt_none)
        setStatusIcon(R.id.bt_icon, btName != null)

        // Open from the background
        findViewById<TextView>(R.id.overlay_status).setText(
            if (overlayOk) R.string.andromi_overlay_ok else R.string.andromi_overlay_needed)
        findViewById<MaterialButton>(R.id.overlay_button).visibility = if (overlayOk) View.GONE else View.VISIBLE
        setStatusIcon(R.id.overlay_icon, overlayOk)
    }

    private fun setStatusIcon(id: Int, ok: Boolean) {
        val icon = findViewById<ImageView>(id)
        icon.setImageResource(if (ok) R.drawable.ic_check_circle_24 else R.drawable.ic_error_outline_24)
        val color = MaterialColors.getColor(icon,
            if (ok) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorError)
        icon.imageTintList = ColorStateList.valueOf(color)
    }

    private fun runRootSetup() {
        if (rootRunning) return
        rootRunning = true
        render()
        lifecycleScope.launch {
            val result = RootSetup.run(this@AutoStartSetupActivity)
            rootRunning = false
            val manual = findViewById<View>(R.id.root_manual)
            when (result) {
                RootSetup.Result.Done -> {
                    manual.visibility = View.GONE
                    toast(R.string.andromi_root_done)
                }
                RootSetup.Result.NoRoot -> {
                    manual.visibility = View.VISIBLE
                    toast(R.string.andromi_root_denied)
                }
                is RootSetup.Result.Failed -> {
                    manual.visibility = View.VISIBLE
                    ToastUtils.showToast(this@AutoStartSetupActivity,
                        getString(R.string.andromi_root_failed, result.message), Toast.LENGTH_LONG, force = true)
                }
            }
            render()
        }
    }

    private fun manualCommands(): String =
        RootSetup.commands(this).joinToString("\n") { "su -c '$it'" }

    private fun chooseBluetooth() {
        val adapter = BluetoothHelper.getBluetoothAdapter(this)
        if (adapter == null || !adapter.isEnabled) {
            toast(R.string.andromi_bt_off)
            return
        }
        BluetoothDevicePicker.show(this, R.string.select_bt_device, settings.autoStartBluetoothDeviceMacs) { chosen ->
            settings.autoStartBluetoothDeviceMacs = chosen
            Settings.syncAutoStartBtMacsToDeviceStorage(this, chosen)
            val first = chosen.firstOrNull()
            settings.autoStartBluetoothDeviceName = first?.let { mac ->
                try { BluetoothDevicePicker.labelFor(adapter.getRemoteDevice(mac)) } catch (e: Exception) { "" }
            } ?: ""
            render()
        }
    }

    private fun bluetoothName(): String? {
        val macs = settings.autoStartBluetoothDeviceMacs
        if (macs.isEmpty()) return null
        return BluetoothDevicePicker.summaryFor(this, macs) { mac ->
            if (settings.autoStartBluetoothDeviceMac == mac && settings.autoStartBluetoothDeviceName.isNotEmpty())
                settings.autoStartBluetoothDeviceName else null
        } ?: macs.first()
    }

    private fun canOpenFromBackground(): Boolean =
        Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(this)

    private fun toast(res: Int) = ToastUtils.showToast(this, res, Toast.LENGTH_SHORT, force = true)
}
