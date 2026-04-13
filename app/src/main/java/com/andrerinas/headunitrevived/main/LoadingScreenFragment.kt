package com.andrerinas.headunitrevived.main

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.andrerinas.headunitrevived.App
import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.PickMediaContract
import com.andrerinas.headunitrevived.utils.Settings
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

class LoadingScreenFragment : Fragment() {

    private lateinit var settings: Settings

    private lateinit var previewArea: FrameLayout
    private lateinit var previewPlaceholder: View
    private lateinit var previewImage: ImageView
    private lateinit var previewVideo: VideoView
    private lateinit var previewStatusText: View
    private lateinit var toggleContainer: View
    private lateinit var toggleShowText: Switch
    private lateinit var btnSelect: View
    private lateinit var btnRemove: View
    private lateinit var fullscreenOverlay: FrameLayout
    private lateinit var fullscreenImage: ImageView
    private lateinit var fullscreenVideo: VideoView
    private lateinit var fullscreenStatusText: View

    private val filePicker = registerForActivityResult(PickMediaContract()) { uri ->
        uri?.let { handleFileSelected(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_loading_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settings = App.provide(requireContext()).settings

        // Bind views
        previewArea = view.findViewById(R.id.preview_area)
        previewPlaceholder = view.findViewById(R.id.preview_placeholder)
        previewImage = view.findViewById(R.id.preview_image)
        previewVideo = view.findViewById(R.id.preview_video)
        previewStatusText = view.findViewById(R.id.preview_status_text)
        toggleContainer = view.findViewById(R.id.toggle_container)
        toggleShowText = view.findViewById(R.id.toggle_show_text)
        btnSelect = view.findViewById(R.id.btn_select_file)
        btnRemove = view.findViewById(R.id.btn_remove)
        fullscreenOverlay = view.findViewById(R.id.fullscreen_overlay)
        fullscreenImage = view.findViewById(R.id.fullscreen_image)
        fullscreenVideo = view.findViewById(R.id.fullscreen_video)
        fullscreenStatusText = view.findViewById(R.id.fullscreen_status_text)

        // Set preview height to match screen aspect ratio
        previewArea.post {
            val width = previewArea.width
            if (width > 0) {
                val dm = resources.displayMetrics
                val ratio = dm.heightPixels.toFloat() / dm.widthPixels.toFloat()
                val height = (width * ratio).toInt().coerceIn(120, 600)
                previewArea.layoutParams = previewArea.layoutParams.apply { this.height = height }
            }
        }

        // Resolution recommendation
        val dm = resources.displayMetrics
        view.findViewById<TextView>(R.id.recommendation_text)?.text =
            getString(R.string.loading_screen_recommendation, dm.widthPixels, dm.heightPixels)

        // Toolbar
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { navigateBack() }

        // Back press
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreenOverlay.visibility == View.VISIBLE) {
                    hideFullscreen()
                } else {
                    navigateBack()
                }
            }
        })

        // Toggle
        toggleShowText.isChecked = settings.loadingScreenShowText
        toggleShowText.setOnCheckedChangeListener { _, isChecked ->
            settings.loadingScreenShowText = isChecked
            updateStatusTextVisibility()
        }

        // Select file
        btnSelect.setOnClickListener {
            try {
                filePicker.launch(Unit)
            } catch (e: Exception) {
                AppLog.e("Failed to launch file picker: ${e.message}")
                Toast.makeText(context, R.string.loading_screen_file_error, Toast.LENGTH_SHORT).show()
            }
        }

        // Remove
        btnRemove.setOnClickListener {
            removeMedia()
        }

        // Preview tap → fullscreen
        previewArea.setOnClickListener {
            if (!settings.loadingScreenMediaPath.isNullOrEmpty()) {
                showFullscreen()
            }
        }

        // Fullscreen tap → close
        fullscreenOverlay.setOnClickListener {
            hideFullscreen()
        }

        // Load current state
        refreshUI()
    }

    override fun onPause() {
        super.onPause()
        stopAllVideo()
    }

    override fun onDestroyView() {
        stopAllVideo()
        super.onDestroyView()
    }

    private fun navigateBack() {
        try {
            if (!findNavController().navigateUp()) {
                requireActivity().finish()
            }
        } catch (e: Exception) {
            requireActivity().finish()
        }
    }

    // --- UI State ---

    private fun refreshUI() {
        val path = settings.loadingScreenMediaPath
        val type = settings.loadingScreenMediaType
        val hasMedia = !path.isNullOrEmpty() && !type.isNullOrEmpty() && File(path).exists()

        if (hasMedia) {
            previewPlaceholder.visibility = View.GONE
            toggleContainer.visibility = View.VISIBLE
            btnRemove.visibility = View.VISIBLE
            loadMedia(previewImage, previewVideo, path, type)
        } else {
            previewPlaceholder.visibility = View.VISIBLE
            previewImage.visibility = View.GONE
            previewVideo.visibility = View.GONE
            toggleContainer.visibility = View.GONE
            btnRemove.visibility = View.GONE
            // Clear stale settings if file doesn't exist
            if (!path.isNullOrEmpty()) {
                settings.loadingScreenMediaPath = ""
                settings.loadingScreenMediaType = ""
            }
        }

        updateStatusTextVisibility()
    }

    private fun updateStatusTextVisibility() {
        val hasMedia = !settings.loadingScreenMediaPath.isNullOrEmpty()
        val show = hasMedia && settings.loadingScreenShowText
        previewStatusText.visibility = if (show) View.VISIBLE else View.GONE
        fullscreenStatusText.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun loadMedia(imageView: ImageView, videoView: VideoView, path: String, type: String) {
        val file = File(path)
        if (!file.exists()) return

        when (type) {
            "image" -> {
                videoView.visibility = View.GONE
                imageView.visibility = View.VISIBLE
                try {
                    Glide.with(this).load(file).into(imageView)
                } catch (e: Exception) {
                    AppLog.e("Failed to load image: ${e.message}")
                }
            }
            "gif" -> {
                videoView.visibility = View.GONE
                imageView.visibility = View.VISIBLE
                try {
                    Glide.with(this).asGif().load(file).into(imageView)
                } catch (e: Exception) {
                    AppLog.e("Failed to load GIF: ${e.message}")
                }
            }
            "video" -> {
                imageView.visibility = View.GONE
                videoView.visibility = View.VISIBLE
                try {
                    videoView.setVideoPath(file.absolutePath)
                    videoView.setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                    }
                    videoView.setOnErrorListener { _, _, _ ->
                        AppLog.e("Error playing video preview")
                        videoView.visibility = View.GONE
                        true
                    }
                    videoView.start()
                } catch (e: Exception) {
                    AppLog.e("Failed to play video: ${e.message}")
                    videoView.visibility = View.GONE
                }
            }
        }
    }

    // --- File Selection ---

    private fun handleFileSelected(uri: Uri) {
        val ctx = context ?: return
        val contentResolver = ctx.contentResolver

        // Validate MIME type
        val mimeType = contentResolver.getType(uri)
        val mediaType = when {
            mimeType == null -> null
            mimeType == "image/gif" -> "gif"
            mimeType.startsWith("image/") -> "image"
            mimeType.startsWith("video/") -> "video"
            else -> null
        }
        if (mediaType == null) {
            Toast.makeText(ctx, R.string.loading_screen_unsupported_format, Toast.LENGTH_SHORT).show()
            return
        }

        // Validate file size (10MB)
        try {
            val size = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1
            if (size > 10L * 1024 * 1024) {
                Toast.makeText(ctx, R.string.loading_screen_file_too_large, Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            AppLog.w("Could not check file size: ${e.message}")
        }

        // Extension from MIME
        val ext = when (mimeType) {
            "image/gif" -> "gif"
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            "video/mp4" -> "mp4"
            "video/x-matroska" -> "mkv"
            "video/webm" -> "webm"
            "video/3gpp" -> "3gp"
            else -> if (mimeType?.startsWith("video/") == true) "mp4" else "img"
        }

        // Copy to internal storage
        val dir = File(ctx.filesDir, "loading_media")
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // Remove previous

        val destFile = File(dir, "loading_screen.$ext")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not open input stream")
        } catch (e: Exception) {
            AppLog.e("Failed to copy file: ${e.message}")
            Toast.makeText(ctx, R.string.loading_screen_file_error, Toast.LENGTH_SHORT).show()
            return
        }

        // Save settings
        settings.loadingScreenMediaPath = destFile.absolutePath
        settings.loadingScreenMediaType = mediaType

        // Stop any playing video before refreshing
        stopAllVideo()
        refreshUI()
    }

    private fun removeMedia() {
        val path = settings.loadingScreenMediaPath
        if (!path.isNullOrEmpty()) {
            try { File(path).delete() } catch (_: Exception) {}
        }
        settings.loadingScreenMediaPath = ""
        settings.loadingScreenMediaType = ""
        settings.loadingScreenShowText = false

        stopAllVideo()
        Glide.with(this).clear(previewImage)
        Glide.with(this).clear(fullscreenImage)
        refreshUI()
    }

    // --- Fullscreen Preview ---

    private fun showFullscreen() {
        val path = settings.loadingScreenMediaPath
        val type = settings.loadingScreenMediaType
        if (path.isNullOrEmpty() || type.isNullOrEmpty()) return

        fullscreenOverlay.visibility = View.VISIBLE
        fullscreenOverlay.alpha = 0f
        fullscreenOverlay.animate().alpha(1f).setDuration(200).start()

        loadMedia(fullscreenImage, fullscreenVideo, path, type)

        // Ken Burns effect for static images in fullscreen
        if (type == "image") {
            val scaleAnim = ObjectAnimator.ofPropertyValuesHolder(
                fullscreenImage,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.05f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.05f)
            )
            scaleAnim.duration = 8000
            scaleAnim.repeatMode = ObjectAnimator.REVERSE
            scaleAnim.repeatCount = ObjectAnimator.INFINITE
            scaleAnim.start()
            fullscreenImage.tag = scaleAnim
        }

        updateStatusTextVisibility()
    }

    private fun hideFullscreen() {
        fullscreenOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            fullscreenOverlay.visibility = View.GONE
            // Stop fullscreen video
            try { if (fullscreenVideo.isPlaying) fullscreenVideo.stopPlayback() } catch (_: Exception) {}
            fullscreenVideo.visibility = View.GONE
            // Cancel Ken Burns
            (fullscreenImage.tag as? ObjectAnimator)?.cancel()
            fullscreenImage.scaleX = 1f
            fullscreenImage.scaleY = 1f
            Glide.with(this@LoadingScreenFragment).clear(fullscreenImage)
        }.start()
    }

    private fun stopAllVideo() {
        try { if (previewVideo.isPlaying) previewVideo.stopPlayback() } catch (_: Exception) {}
        try { if (fullscreenVideo.isPlaying) fullscreenVideo.stopPlayback() } catch (_: Exception) {}
    }
}
