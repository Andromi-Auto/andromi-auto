package com.andrerinas.openheadunit.main

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.ColorUtils
import com.andrerinas.openheadunit.utils.PickImageContract
import com.andrerinas.openheadunit.utils.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CustomizationFragment : Fragment() {

    private lateinit var settings: Settings
    private var bgDefaultView: View? = null
    private var bgCustomImageView: ImageView? = null
    private var txtStatus: TextView? = null
    private var btnSelectImage: MaterialButton? = null
    private var btnResetBg: MaterialButton? = null

    // Button color previews & rows
    private var previewBtnSelfMode: MaterialButton? = null
    private var previewBtnUsb: MaterialButton? = null
    private var previewBtnWifi: MaterialButton? = null
    private var previewBtnSettings: MaterialButton? = null

    private var indicatorSelfMode: View? = null
    private var indicatorUsb: View? = null
    private var indicatorWifi: View? = null
    private var indicatorSettings: View? = null

    private var rowColorSelfMode: View? = null
    private var rowColorUsb: View? = null
    private var rowColorWifi: View? = null
    private var rowColorSettings: View? = null
    private var btnResetColors: MaterialButton? = null

    private val imagePicker = registerForActivityResult(PickImageContract()) { uri ->
        uri?.let { handleImageSelected(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_customization, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = App.provide(requireContext()).settings

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Background Image views
        bgDefaultView = view.findViewById(R.id.bg_default_view)
        bgCustomImageView = view.findViewById(R.id.bg_custom_image_view)
        txtStatus = view.findViewById(R.id.txt_status)
        btnSelectImage = view.findViewById(R.id.btn_select_image)
        btnResetBg = view.findViewById(R.id.btn_reset_bg)

        // Button Color views
        previewBtnSelfMode = view.findViewById(R.id.preview_btn_self_mode)
        previewBtnUsb = view.findViewById(R.id.preview_btn_usb)
        previewBtnWifi = view.findViewById(R.id.preview_btn_wifi)
        previewBtnSettings = view.findViewById(R.id.preview_btn_settings)

        indicatorSelfMode = view.findViewById(R.id.indicator_self_mode)
        indicatorUsb = view.findViewById(R.id.indicator_usb)
        indicatorWifi = view.findViewById(R.id.indicator_wifi)
        indicatorSettings = view.findViewById(R.id.indicator_settings)

        rowColorSelfMode = view.findViewById(R.id.row_color_self_mode)
        rowColorUsb = view.findViewById(R.id.row_color_usb)
        rowColorWifi = view.findViewById(R.id.row_color_wifi)
        rowColorSettings = view.findViewById(R.id.row_color_settings)
        btnResetColors = view.findViewById(R.id.btn_reset_colors)

        // Background listeners
        btnSelectImage?.setOnClickListener {
            try {
                imagePicker.launch(Unit)
            } catch (e: Exception) {
                AppLog.e("Failed to launch image picker: ${e.message}")
                Toast.makeText(requireContext(), R.string.loading_screen_file_error, Toast.LENGTH_SHORT).show()
            }
        }

        btnResetBg?.setOnClickListener {
            resetToDefault()
        }

        // Button Color listeners
        rowColorSelfMode?.setOnClickListener {
            showColorPickerDialog(
                R.string.btn_color_self_mode,
                settings.customSelfModeButtonColor,
                R.drawable.gradient_blue,
                R.drawable.ic_launch_white
            ) { color ->
                settings.customSelfModeButtonColor = color
            }
        }

        rowColorUsb?.setOnClickListener {
            showColorPickerDialog(
                R.string.btn_color_usb,
                settings.customUsbButtonColor,
                R.drawable.gradient_orange,
                R.drawable.ic_usb_white
            ) { color ->
                settings.customUsbButtonColor = color
            }
        }

        rowColorWifi?.setOnClickListener {
            showColorPickerDialog(
                R.string.btn_color_wifi,
                settings.customWifiButtonColor,
                R.drawable.gradient_purple,
                R.drawable.ic_network_wifi_white
            ) { color ->
                settings.customWifiButtonColor = color
            }
        }

        rowColorSettings?.setOnClickListener {
            showColorPickerDialog(
                R.string.btn_color_settings,
                settings.customSettingsButtonColor,
                R.drawable.gradient_darkblue,
                R.drawable.ic_settings_white
            ) { color ->
                settings.customSettingsButtonColor = color
            }
        }

        btnResetColors?.setOnClickListener {
            resetAllButtonColors()
        }

        refreshUI()
    }

    private fun handleImageSelected(uri: Uri) {
        val ctx = context ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val destFile = File(ctx.filesDir, "custom_home_bg.png")
            var success = false

            try {
                if (uri.scheme == "file") {
                    val srcFile = uri.path?.let { File(it) }
                    if (srcFile != null && srcFile.exists()) {
                        srcFile.inputStream().use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        success = true
                    }
                } else {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                        success = true
                    }
                }
            } catch (e: Exception) {
                AppLog.e("Failed to copy custom background image: ${e.message}")
                success = false
            }

            withContext(Dispatchers.Main) {
                if (success && destFile.exists() && destFile.length() > 0) {
                    settings.homeBackgroundImagePath = destFile.absolutePath
                    refreshUI()
                    notifyMainActivityBackgroundChanged()
                } else {
                    Toast.makeText(ctx, R.string.loading_screen_file_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resetToDefault() {
        val path = settings.homeBackgroundImagePath
        if (path.isNotEmpty()) {
            val file = File(path)
            if (file.exists()) {
                try { file.delete() } catch (_: Exception) {}
            }
        }
        settings.homeBackgroundImagePath = ""
        refreshUI()
        notifyMainActivityBackgroundChanged()
    }

    private fun resetAllButtonColors() {
        settings.customSelfModeButtonColor = 0
        settings.customUsbButtonColor = 0
        settings.customWifiButtonColor = 0
        settings.customSettingsButtonColor = 0
        refreshUI()
    }

    private fun refreshUI() {
        val ctx = context ?: return
        val path = settings.homeBackgroundImagePath
        val hasCustomImage = path.isNotEmpty() && File(path).exists()

        if (hasCustomImage) {
            bgDefaultView?.visibility = View.GONE
            bgCustomImageView?.let { iv ->
                iv.visibility = View.VISIBLE
                Glide.with(this)
                    .load(File(path))
                    .centerCrop()
                    .into(iv)
            }
            txtStatus?.text = getString(R.string.home_background_custom)
            btnResetBg?.isEnabled = true
        } else {
            bgCustomImageView?.let { iv ->
                Glide.with(this).clear(iv)
                iv.visibility = View.GONE
            }
            bgDefaultView?.visibility = View.VISIBLE
            txtStatus?.text = getString(R.string.home_background_default)
            btnResetBg?.isEnabled = false
        }

        // Update button color previews & indicators
        updateButtonPreview(
            previewBtnSelfMode,
            indicatorSelfMode,
            settings.customSelfModeButtonColor,
            R.drawable.gradient_blue,
            ctx
        )
        updateButtonPreview(
            previewBtnUsb,
            indicatorUsb,
            settings.customUsbButtonColor,
            R.drawable.gradient_orange,
            ctx
        )
        updateButtonPreview(
            previewBtnWifi,
            indicatorWifi,
            settings.customWifiButtonColor,
            R.drawable.gradient_purple,
            ctx
        )
        updateButtonPreview(
            previewBtnSettings,
            indicatorSettings,
            settings.customSettingsButtonColor,
            R.drawable.gradient_darkblue,
            ctx
        )

        val hasCustomColors = settings.customSelfModeButtonColor != 0 ||
                settings.customUsbButtonColor != 0 ||
                settings.customWifiButtonColor != 0 ||
                settings.customSettingsButtonColor != 0
        btnResetColors?.isEnabled = hasCustomColors
    }

    private fun updateButtonPreview(
        previewButton: MaterialButton?,
        indicatorView: View?,
        customColor: Int,
        defaultDrawableRes: Int,
        ctx: Context
    ) {
        if (customColor != 0) {
            val customDrawable = ColorUtils.createGradientDrawable(customColor, 32f, ctx)
            val indicatorDrawable = ColorUtils.createGradientDrawable(customColor, 12f, ctx)
            previewButton?.background = customDrawable
            indicatorView?.background = indicatorDrawable
        } else {
            val defaultDrawable = ContextCompat.getDrawable(ctx, defaultDrawableRes)
            previewButton?.background = defaultDrawable
            indicatorView?.background = ContextCompat.getDrawable(ctx, defaultDrawableRes)
        }
    }

    private fun showColorPickerDialog(
        titleRes: Int,
        currentColor: Int,
        defaultDrawableRes: Int,
        iconRes: Int,
        onSave: (Int) -> Unit
    ) {
        val ctx = context ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)

        val txtTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        val previewButton = dialogView.findViewById<MaterialButton>(R.id.dialog_preview_button)
        val presetGrid = dialogView.findViewById<GridLayout>(R.id.preset_grid)
        val hexEditText = dialogView.findViewById<TextInputEditText>(R.id.hex_input_edit_text)

        txtTitle.setText(titleRes)
        previewButton.setIconResource(iconRes)

        var selectedColor = currentColor

        fun updateDialogPreview(color: Int) {
            selectedColor = color
            if (color != 0) {
                previewButton.background = ColorUtils.createGradientDrawable(color, 36f, ctx)
            } else {
                previewButton.background = ContextCompat.getDrawable(ctx, defaultDrawableRes)
            }
        }

        updateDialogPreview(currentColor)

        var isInternalTextChange = false
        if (currentColor != 0) {
            isInternalTextChange = true
            val hexStr = String.format("#%06X", 0xFFFFFF and currentColor)
            hexEditText.setText(hexStr)
            isInternalTextChange = false
        } else {
            isInternalTextChange = true
            hexEditText.setText("")
            isInternalTextChange = false
        }

        val presets = listOf(
            Pair("Default", 0),
            Pair("Cyan", Color.parseColor("#2CC6F2")),
            Pair("Blue", Color.parseColor("#027FEE")),
            Pair("Orange", Color.parseColor("#F4A157")),
            Pair("Red", Color.parseColor("#E74C3C")),
            Pair("Green", Color.parseColor("#2ECC71")),
            Pair("Purple", Color.parseColor("#DE93FD")),
            Pair("Pink", Color.parseColor("#E91E63")),
            Pair("Yellow", Color.parseColor("#F1C40F")),
            Pair("Teal", Color.parseColor("#009688")),
            Pair("Slate", Color.parseColor("#576D82")),
            Pair("Dark", Color.parseColor("#2C3E50"))
        )

        presetGrid.removeAllViews()
        val density = ctx.resources.displayMetrics.density
        val sizePx = (36 * density).toInt()
        val marginPx = (4 * density).toInt()

        presets.forEach { (_, colorInt) ->
            val swatch = View(ctx)
            val lp = GridLayout.LayoutParams().apply {
                width = sizePx
                height = sizePx
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            swatch.layoutParams = lp

            if (colorInt == 0) {
                swatch.background = ContextCompat.getDrawable(ctx, defaultDrawableRes)
            } else {
                swatch.background = ColorUtils.createGradientDrawable(colorInt, 18f, ctx)
            }

            swatch.setOnClickListener {
                updateDialogPreview(colorInt)
                isInternalTextChange = true
                if (colorInt != 0) {
                    hexEditText.setText(String.format("#%06X", 0xFFFFFF and colorInt))
                } else {
                    hexEditText.setText("")
                }
                isInternalTextChange = false
            }
            presetGrid.addView(swatch)
        }

        hexEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isInternalTextChange) return
                val input = s?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    val parsed = ColorUtils.parseColorSafely(input, -1)
                    if (parsed != -1) {
                        updateDialogPreview(parsed)
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        MaterialAlertDialogBuilder(ctx)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSave(selectedColor)
                refreshUI()
            }
            .setNeutralButton(R.string.color_preset_default) { _, _ ->
                onSave(0)
                refreshUI()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun notifyMainActivityBackgroundChanged() {
        (activity as? MainActivity)?.applyCustomHomeBackground()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bgCustomImageView?.let {
            try { Glide.with(this).clear(it) } catch (_: Exception) {}
        }
    }
}
