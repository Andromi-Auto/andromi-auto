package com.andrerinas.headunitrevived.main

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.headunitrevived.App
import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.decoder.MicRecorder
import com.andrerinas.headunitrevived.main.settings.SettingItem
import com.andrerinas.headunitrevived.main.settings.SettingsAdapter
import com.andrerinas.headunitrevived.utils.Settings

class SettingsFragment : Fragment() {
    private lateinit var settings: Settings
    private lateinit var settingsRecyclerView: RecyclerView
    private lateinit var settingsAdapter: SettingsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settings = App.provide(requireContext()).settings

        settingsRecyclerView = view.findViewById(R.id.settingsRecyclerView)
        settingsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        updateSettingsList()
    }

    private fun updateSettingsList() {
        val items = mutableListOf<SettingItem>()

        // --- General Settings ---
        items.add(SettingItem.CategoryHeader(R.string.category_general))

        items.add(SettingItem.SettingEntry(
            id = "keymap",
            nameResId = R.string.keymap,
            value = getString(R.string.keymap_description), // Use new string resource
            onClick = { _ ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.main_content, KeymapFragment())
                    .addToBackStack(null)
                    .commit()
            }
        ))

        items.add(SettingItem.SettingEntry(
            id = "gpsNavigation",
            nameResId = R.string.gps_for_navigation,
            value = if (settings.useGpsForNavigation) getString(R.string.enabled) else getString(R.string.disabled),
            onClick = { _ ->
                settings.useGpsForNavigation = !settings.useGpsForNavigation
                updateSettingsList() // Refresh the list
            }
        ))

        items.add(SettingItem.SettingEntry(
            id = "nightMode",
            nameResId = R.string.night_mode,
            value = resources.getStringArray(R.array.night_mode)[settings.nightMode.value],
            onClick = { _ ->
                val nightModeTitles = resources.getStringArray(R.array.night_mode)
                val currentNightModeIndex = settings.nightMode.value
                
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.night_mode)
                    .setSingleChoiceItems(nightModeTitles, currentNightModeIndex) { dialog, which ->
                        val newMode = Settings.NightMode.fromInt(which)!!
                        settings.nightMode = newMode
                        dialog.dismiss()
                        updateSettingsList() // Refresh the list
                    }
                    .show()
            }
        ))

        items.add(SettingItem.SettingEntry(
            id = "micSampleRate",
            nameResId = R.string.mic_sample_rate,
            value = "${settings.micSampleRate / 1000}kHz",
            onClick = { _ ->
                val currentSampleRateIndex = Settings.MicSampleRates.indexOf(settings.micSampleRate)
                val sampleRateNames = Settings.MicSampleRates.map { "${it / 1000}kHz" }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.mic_sample_rate)
                    .setSingleChoiceItems(sampleRateNames, currentSampleRateIndex) { dialog, which ->
                        val newValue = Settings.MicSampleRates.elementAt(which)

                        val recorder: MicRecorder? = try { MicRecorder(newValue, requireContext().applicationContext) } catch (e: Exception) { null }

                        if (recorder == null) {
                            Toast.makeText(activity, "Value not supported: $newValue", Toast.LENGTH_LONG).show()
                        } else {
                            settings.micSampleRate = newValue
                        }
                        dialog.dismiss()
                        updateSettingsList() // Refresh the list
                    }
                    .show()
            }
        ))
        items.add(SettingItem.Divider) // Divider after General category

        // --- Graphic Settings ---
        items.add(SettingItem.CategoryHeader(R.string.category_graphic))
        
        items.add(SettingItem.SettingEntry(
            id = "resolution",
            nameResId = R.string.resolution,
            value = Settings.Resolution.fromId(settings.resolutionId)?.resName ?: "",
            onClick = { _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.change_resolution)
                    .setSingleChoiceItems(Settings.Resolution.allRes, settings.resolutionId) { dialog, which ->
                        settings.resolutionId = which
                        dialog.dismiss()
                        updateSettingsList() // Refresh the list
                    }
                    .show()
            }
        ))

        items.add(SettingItem.SettingEntry(
            id = "dpiPixelDensity",
            nameResId = R.string.dpi,
            value = if (settings.dpiPixelDensity == 0) getString(R.string.auto) else settings.dpiPixelDensity.toString(),
            onClick = { _ ->
                val editView = EditText(requireContext())
                editView.inputType = InputType.TYPE_CLASS_NUMBER
                if (settings.dpiPixelDensity != 0) {
                    editView.setText(settings.dpiPixelDensity.toString())
                }

                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.enter_dpi_value)
                    .setView(editView)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        val inputText = editView.text.toString().trim()
                        val newDpi = inputText.toIntOrNull()
                        if (newDpi != null && newDpi >= 0) {
                            settings.dpiPixelDensity = newDpi
                        } else if (inputText.isNotEmpty()) {
                            Toast.makeText(activity, "Invalid DPI value. Please enter a number or 0 for auto.", Toast.LENGTH_LONG).show()
                        } else {
                            settings.dpiPixelDensity = 0 // If empty, set to auto
                        }
                        dialog.dismiss()
                        updateSettingsList() // Refresh the list
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.cancel()
                    }
                    .show()
            }
        ))

        items.add(SettingItem.SettingEntry(
            id = "viewMode",
            nameResId = R.string.view_mode,
            value = if (settings.viewMode == Settings.ViewMode.SURFACE) getString(R.string.surface_view) else getString(R.string.texture_view),
            onClick = { _ ->
                val viewModes = arrayOf(getString(R.string.surface_view), getString(R.string.texture_view))
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.change_view_mode)
                    .setSingleChoiceItems(viewModes, settings.viewMode.value) { dialog, which ->
                        val newViewMode = Settings.ViewMode.fromInt(which)!!
                        settings.viewMode = newViewMode
                        dialog.dismiss()
                        updateSettingsList() // Refresh the list
                    }
                    .show()
            }
        ))
        items.add(SettingItem.Divider) // Divider after Graphic category
        
        // --- Debug Settings ---
        items.add(SettingItem.CategoryHeader(R.string.category_debug))

        items.add(SettingItem.SettingEntry(
            id = "debugMode",
            nameResId = R.string.debug_mode,
            value = if (settings.debugMode) getString(R.string.enabled) else getString(R.string.disabled),
            onClick = { _ ->
                settings.debugMode = !settings.debugMode
                updateSettingsList() // Refresh the list
            }
        ))

        items.add(SettingItem.SettingEntry(
            id = "bluetoothAddress",
            nameResId = R.string.bluetooth_address_s,
            value = settings.bluetoothAddress.ifEmpty { getString(R.string.not_set) },
            onClick = { _ ->
                val editView = EditText(requireContext())
                editView.setText(settings.bluetoothAddress)
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.enter_bluetooth_mac)
                    .setView(editView)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        settings.bluetoothAddress = editView.text.toString().trim()
                        dialog.dismiss()
                        updateSettingsList() // Refresh the list
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.cancel()
                    }
                    .show()
            }
        ))

        items.add(SettingItem.Divider) // Divider after Debug category


        settingsAdapter = SettingsAdapter(items)
        settingsRecyclerView.adapter = settingsAdapter
    }
}