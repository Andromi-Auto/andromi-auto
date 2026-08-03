package com.andrerinas.openheadunit.main

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.AppLog
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

        bgDefaultView = view.findViewById(R.id.bg_default_view)
        bgCustomImageView = view.findViewById(R.id.bg_custom_image_view)
        txtStatus = view.findViewById(R.id.txt_status)
        btnSelectImage = view.findViewById(R.id.btn_select_image)
        btnResetBg = view.findViewById(R.id.btn_reset_bg)

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

    private fun refreshUI() {
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
