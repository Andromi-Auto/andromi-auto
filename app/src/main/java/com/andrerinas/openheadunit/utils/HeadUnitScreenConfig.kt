package com.andrerinas.openheadunit.utils

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.NarrowBandProfilePolicy
import com.andrerinas.openheadunit.aap.protocol.proto.Control
import com.andrerinas.openheadunit.connection.wifi.direct.WifiBandCapability
import com.andrerinas.openheadunit.decoder.video.VideoDecoder
import kotlin.math.roundToInt

object HeadUnitScreenConfig {

    private var screenWidthPx: Int = 0
    private var screenHeightPx: Int = 0
    private var density: Float = 1.0f
    private var densityDpi: Int = 240
    private var scaleFactor: Float = 1.0f
    private var isSmallScreen: Boolean = true
    private var isPortraitScaled: Boolean = false
    private var isInitialized: Boolean = false
    private var lastSettingsHash: Int = 0
    
    // How the negotiated video is fitted into the panel (FILL/CONTAIN/COVER, see Settings.VideoFitMode).
    private var videoFitMode: Settings.VideoFitMode = Settings.VideoFitMode.FILL

    // Forced scale for older devices (Legacy fix)
    var forcedScale: Boolean = false
        private set

    var negotiatedResolutionType: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480
    var isResolutionLocked: Boolean = false
        private set

    private lateinit var currentSettings: Settings // Store settings instance

    /** Application context, so [recalculate] can ask the radio what band it has. Never an Activity. */
    private var appContext: Context? = null

    // System Insets (Bars/Cutouts)
    var systemInsetLeft: Int = 0
        private set
    var systemInsetTop: Int = 0
        private set
    var systemInsetRight: Int = 0
        private set
    var systemInsetBottom: Int = 0
        private set

    // Raw Screen Dimensions (Full Display)
    private var realScreenWidthPx: Int = 0
    private var realScreenHeightPx: Int = 0

    // What ServiceDiscoveryResponse actually put on the wire. init() re-reads the display metrics
    // on every scale update, so the live margins can move under a session that already announced
    // its own; this is what the drift is measured against.
    private var announcedWidthMargin: Int = MarginAnnouncementPolicy.NOT_ANNOUNCED
    private var announcedHeightMargin: Int = MarginAnnouncementPolicy.NOT_ANNOUNCED

    /**
     * Raised when the live margins leave the announced ones. The listener records what it sends;
     * a false keeps the old announcement standing so the next recalculate retries.
     */
    var onMarginsDiverged: (() -> Boolean)? = null

    // The listener redraws, which re-enters init() and can land back here. One notification at a time.
    private var notifyingMarginDivergence: Boolean = false

    fun recordAnnouncedMargins(widthMargin: Int, heightMargin: Int) {
        announcedWidthMargin = widthMargin
        announcedHeightMargin = heightMargin
    }

    /** True when the live margins are already on the wire, so sending them again would only repeat it. */
    fun marginsMatchAnnounced(): Boolean =
        announcedWidthMargin != MarginAnnouncementPolicy.NOT_ANNOUNCED &&
            announcedHeightMargin != MarginAnnouncementPolicy.NOT_ANNOUNCED &&
            !MarginAnnouncementPolicy.shouldReannounce(
                announcedWidthMargin, announcedHeightMargin, getWidthMargin(), getHeightMargin()
            )

    fun clearAnnouncedMargins() {
        announcedWidthMargin = MarginAnnouncementPolicy.NOT_ANNOUNCED
        announcedHeightMargin = MarginAnnouncementPolicy.NOT_ANNOUNCED
    }


    fun init(context: Context, displayMetrics: DisplayMetrics, settings: Settings) {
        videoFitMode = settings.videoFitMode
        forcedScale = settings.forcedScale && settings.viewMode == Settings.ViewMode.SURFACE

        val realW: Int
        val realH: Int
        val usableW: Int
        val usableH: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
            val windowManager = context.getSystemService(android.view.WindowManager::class.java)
            val bounds = windowManager.currentWindowMetrics.bounds
            // On API 30+, bounds on an Activity context often return the usable area.
            // We use the displayMetrics as a fallback for the physical area.
            realW = displayMetrics.widthPixels
            realH = displayMetrics.heightPixels
            usableW = bounds.width()
            usableH = bounds.height()
        } else { // Older APIs
            @Suppress("DEPRECATION")
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val display = windowManager.defaultDisplay
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            realW = size.x
            realH = size.y

            @Suppress("DEPRECATION")
            display.getSize(size)
            usableW = size.x
            usableH = size.y
        }

        val finalRealW: Int
        val finalRealH: Int
        val finalUsableW: Int
        val finalUsableH: Int

        val screenOrientation = settings.screenOrientation
        val configOrientation = context.resources.configuration.orientation
        val isConfigLandscape = configOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val isConfigPortrait = configOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

        if (screenOrientation == Settings.ScreenOrientation.LANDSCAPE ||
            screenOrientation == Settings.ScreenOrientation.LANDSCAPE_REVERSE ||
            ((screenOrientation == Settings.ScreenOrientation.AUTO || screenOrientation == Settings.ScreenOrientation.SYSTEM) && isConfigLandscape)) {
            finalRealW = Math.max(realW, realH)
            finalRealH = Math.min(realW, realH)
            finalUsableW = Math.max(usableW, usableH)
            finalUsableH = Math.min(usableW, usableH)
        } else if (screenOrientation == Settings.ScreenOrientation.PORTRAIT ||
                   screenOrientation == Settings.ScreenOrientation.PORTRAIT_REVERSE ||
                   ((screenOrientation == Settings.ScreenOrientation.AUTO || screenOrientation == Settings.ScreenOrientation.SYSTEM) && isConfigPortrait)) {
            finalRealW = Math.min(realW, realH)
            finalRealH = Math.max(realW, realH)
            finalUsableW = Math.min(usableW, usableH)
            finalUsableH = Math.max(usableW, usableH)
        } else {
            finalRealW = realW
            finalRealH = realH
            finalUsableW = usableW
            finalUsableH = usableH
        }

        AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Raw size: ${realW}x${realH}, usable: ${usableW}x${usableH}, orientation setting: $screenOrientation, configOrientation: $configOrientation")
        AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Final normalized size: ${finalRealW}x${finalRealH}, usable: ${finalUsableW}x${finalUsableH}")

        // Only update if dimensions or settings changed
        val currentHash = computeSettingsHash(settings)
        if (isInitialized && realScreenWidthPx == finalRealW && realScreenHeightPx == finalRealH && lastSettingsHash == currentHash) {
            return
        }

        // If settings changed (e.g. orientation swap), unlock resolution before recalculating
        if (isInitialized && lastSettingsHash != 0 && lastSettingsHash != currentHash) {
            AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Settings changed ($lastSettingsHash -> $currentHash). Unlocking resolution.")
            unlockResolution()
        }

        isInitialized = true
        lastSettingsHash = currentHash
        currentSettings = settings
        appContext = context.applicationContext

        // Determine if we are planning to hide the bars (Immersive)
        val immersive = settings.fullscreenMode == Settings.FullscreenMode.IMMERSIVE ||
                        settings.fullscreenMode == Settings.FullscreenMode.IMMERSIVE_WITH_NOTCH

        // THE ANCHOR:
        // If we are immersive, our "World" is the physical screen.
        // If we are NOT, our "World" is limited to the usable window area (no lying to AA).
        val defaultAnchorW = if (immersive) finalRealW else finalUsableW
        val defaultAnchorH = if (immersive) finalRealH else finalUsableH

        density = displayMetrics.density
        densityDpi = displayMetrics.densityDpi

        // Initial Insets: For non-immersive, the bars are already baked into the anchor (realSize = 736),
        // so we start with 0 system insets and just add manual settings.
        systemInsetLeft = settings.insetLeft
        systemInsetTop = settings.insetTop
        systemInsetRight = settings.insetRight
        systemInsetBottom = settings.insetBottom

        // Check if we have cached surface dimensions from a previous session.
        // If the settings haven't changed (same hash), use the cached values
        // to avoid a mid-session UpdateUiConfigRequest and potential flicker.
        val cachedW = settings.cachedSurfaceWidth
        val cachedH = settings.cachedSurfaceHeight
        val cachedHash = settings.cachedSurfaceSettingsHash

        if (cachedW > 0 && cachedH > 0 && cachedHash == currentHash) {
            val normalizedCachedW: Int
            val normalizedCachedH: Int
            if (screenOrientation == Settings.ScreenOrientation.LANDSCAPE ||
                screenOrientation == Settings.ScreenOrientation.LANDSCAPE_REVERSE ||
                ((screenOrientation == Settings.ScreenOrientation.AUTO || screenOrientation == Settings.ScreenOrientation.SYSTEM) && isConfigLandscape)) {
                normalizedCachedW = Math.max(cachedW, cachedH)
                normalizedCachedH = Math.min(cachedW, cachedH)
            } else if (screenOrientation == Settings.ScreenOrientation.PORTRAIT ||
                       screenOrientation == Settings.ScreenOrientation.PORTRAIT_REVERSE ||
                       ((screenOrientation == Settings.ScreenOrientation.AUTO || screenOrientation == Settings.ScreenOrientation.SYSTEM) && isConfigPortrait)) {
                normalizedCachedW = Math.min(cachedW, cachedH)
                normalizedCachedH = Math.max(cachedW, cachedH)
            } else {
                normalizedCachedW = cachedW
                normalizedCachedH = cachedH
            }

            // Cached surface dimensions are the usable area. The anchor includes insets.
            realScreenWidthPx = normalizedCachedW + systemInsetLeft + systemInsetRight
            realScreenHeightPx = normalizedCachedH + systemInsetTop + systemInsetBottom
            AppLog.i("[UI_DEBUG_FIX] HeadUnitScreenConfig: Using cached surface dimensions: ${normalizedCachedW}x${normalizedCachedH} (raw: ${cachedW}x${cachedH}, anchor: ${realScreenWidthPx}x${realScreenHeightPx})")
        } else {
            realScreenWidthPx = defaultAnchorW
            realScreenHeightPx = defaultAnchorH
            if (cachedW > 0) {
                AppLog.i("[UI_DEBUG_FIX] HeadUnitScreenConfig: Cache invalidated (hash mismatch: stored=$cachedHash, current=$currentHash)")
            }
        }

        AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Honest Init | Mode: ${settings.fullscreenMode} | Anchor: ${realScreenWidthPx}x${realScreenHeightPx} | Seeded Insets: L$systemInsetLeft T$systemInsetTop R$systemInsetRight B$systemInsetBottom")

        recalculate()
    }

    fun updateInsets(left: Int, top: Int, right: Int, bottom: Int) {
        if (systemInsetLeft == left && systemInsetTop == top && systemInsetRight == right && systemInsetBottom == bottom) {
            return
        }

        systemInsetLeft = left
        systemInsetTop = top
        systemInsetRight = right
        systemInsetBottom = bottom

        if (isInitialized) {
            recalculate()
        }
    }

    // Native standard resolution for a given panel size, mirroring the AUTO selection so the
    // resolution cap never advertises more than the panel warrants (issue #650).
    /** Map a resolution class to the proto type for the current orientation (shared by the manual
     * selection path and the panel cap, so both agree). */
    private fun protoForResolution(
        res: Settings.Resolution,
        portrait: Boolean
    ): Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType {
        val landscape = res.codec
            ?: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480
        if (!portrait) return landscape
        return when (res) {
            Settings.Resolution._800x480 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._720x1280
            Settings.Resolution._1280x720 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._720x1280
            Settings.Resolution._1920x1080 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1080x1920
            Settings.Resolution._2560x1440 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1440x2560
            Settings.Resolution._3840x2160 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._2160x3840
            else -> landscape
        }
    }

    /**
     * The largest proto resolution the panel can use, in the display's orientation. Deliberately
     * SystemOptimizer.hardCeiling and not panelCeiling: the latter is the recommendation, and
     * capping to it silently overrode the "Use anyway" the settings dialog offers, costing an
     * ultra-wide panel its native width on a resolution the user had picked on purpose.
     */
    private fun hardCeilingForPanel(
        w: Int,
        h: Int,
        portrait: Boolean,
        canHevc: Boolean
    ): Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType {
        return protoForResolution(SystemOptimizer.hardCeiling(w, h, canHevc), portrait)
    }

    /**
     * The ceiling a 2.4 GHz-only radio puts on the resolution, or null when it puts none.
     *
     * Never throws: this runs on every service discovery, and a band read that fails must leave the
     * user's choice alone rather than cost the session.
     */
    private fun narrowBandCeiling(
        isPortraitDisplay: Boolean
    ): Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType? {
        val context = appContext ?: return null
        return try {
            NarrowBandProfilePolicy.linkCeiling(
                supports5Ghz = WifiBandCapability.supports5Ghz(context),
                wirelessSession = App.provide(context).commManager.isWirelessSession,
                capEnabled = currentSettings.narrowBandProfileCap,
            )?.let { protoForResolution(it, isPortraitDisplay) }
        } catch (e: Exception) {
            AppLog.d("HeadUnitScreenConfig: could not evaluate the band ceiling: ${e.message}")
            null
        }
    }

    private fun pixelsOf(type: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType): Long {
        val s = type.toString().replace("_", "")
        return try {
            val parts = s.split("x")
            parts[0].toLong() * parts[1].toLong()
        } catch (e: Exception) {
            0L
        }
    }

    private fun recalculate() {
        // Calculate USABLE area
        screenWidthPx = realScreenWidthPx - systemInsetLeft - systemInsetRight
        screenHeightPx = realScreenHeightPx - systemInsetTop - systemInsetBottom

        if (screenWidthPx <= 0 || screenHeightPx <= 0) {
            screenWidthPx = realScreenWidthPx
            screenHeightPx = realScreenHeightPx
        }

        val selectedResolution = Settings.Resolution.fromId(currentSettings.resolutionId)
        val isPortraitDisplay = screenHeightPx > screenWidthPx
        val canNegotiateHevc = canNegotiateHevcHighResolution()

        // 1. Determine base negotiated resolution
        if (isResolutionLocked) {
            // Safety Check: If the locked resolution's orientation (Landscape/Portrait)
            // no longer matches the display orientation, the lock is stale and must be dropped.
            val isPortraitRes = getNegotiatedHeight() > getNegotiatedWidth()
            if (isPortraitRes != isPortraitDisplay) {
                AppLog.i("[UI_DEBUG] CarScreen: Orientation mismatch detected (Res: ${if(isPortraitRes) "P" else "L"}, Display: ${if(isPortraitDisplay) "P" else "L"}). DROPPING LOCK.")
                unlockResolution()
            } else {
                AppLog.i("[UI_DEBUG] CarScreen: RESOLUTION LOCKED to $negotiatedResolutionType. Usable area is ${screenWidthPx}x${screenHeightPx}. Skipping re-negotiation.")
            }
        }
        
        // A locked session keeps what it already negotiated. This used to fall through to the
        // manual branch, where AUTO carries no codec and the fallback landed on 480p.
        NegotiatedResolutionPolicy.select(
            isLocked = isResolutionLocked,
            selected = selectedResolution,
            panelW = screenWidthPx,
            panelH = screenHeightPx,
            fitMode = videoFitMode,
            hevcSupported = VideoDecoder.isHevcSupported(),
            canHevcHighRes = canNegotiateHevc,
            sdkInt = Build.VERSION.SDK_INT
        )?.let { negotiatedResolutionType = protoForResolution(it, isPortraitDisplay) }

        // Cap to the largest buffer the panel can use, so a small panel never decodes a frame it
        // has to downscale every time, which overloads the MediaTek MDP scaler (issue #650). This
        // is the hard ceiling, not the recommendation: a wide panel uses a 1920-wide buffer in full
        // and only hides rows. min(current, ceiling), so a lower choice is never raised.
        val preCapResolution = negotiatedResolutionType
        val hardCeiling = hardCeilingForPanel(realScreenWidthPx, realScreenHeightPx, isPortraitDisplay, canNegotiateHevc)
        if (pixelsOf(negotiatedResolutionType) > pixelsOf(hardCeiling)) {
            negotiatedResolutionType = hardCeiling
        }
        // And to what the link can carry. Same min(current, ceiling) shape as the panel cap above,
        // so a user already asking for less is never raised to meet it.
        val linkCeiling = narrowBandCeiling(isPortraitDisplay)
        if (linkCeiling != null && pixelsOf(negotiatedResolutionType) > pixelsOf(linkCeiling)) {
            negotiatedResolutionType = linkCeiling
        }
        AppLog.i(
            "[RES_CAP] resolutionId=${currentSettings.resolutionId} " +
                "realScreen=${realScreenWidthPx}x${realScreenHeightPx} usable=${screenWidthPx}x${screenHeightPx} " +
                "portrait=$isPortraitDisplay locked=$isResolutionLocked chosen=$preCapResolution " +
                "capped=$negotiatedResolutionType changed=${preCapResolution != negotiatedResolutionType} " +
                "linkCapped=${linkCeiling ?: "none"}"
        )

        // 2. Perform scaling calculations (now safe because negotiatedResolutionType is set)
        AppLog.i("[UI_DEBUG] CarScreen: usable area ${screenWidthPx}x${screenHeightPx}, using $negotiatedResolutionType")

        val fit = ProjectionGeometryPolicy.fit(
            screenWidthPx, screenHeightPx, getNegotiatedWidth(), getNegotiatedHeight()
        )
        isSmallScreen = fit.isSmallScreen
        scaleFactor = fit.scaleFactor
        // Null on a small screen, where the previous value deliberately stands.
        fit.isPortraitScaled?.let { isPortraitScaled = it }
        
        AppLog.i("[UI_DEBUG] CarScreen isSmallScreen: $isSmallScreen, scaleFactor: $scaleFactor, portraitScaled: $isPortraitScaled, margins: w=${getWidthMargin()}, h=${getHeightMargin()}")

        if (!notifyingMarginDivergence &&
            MarginAnnouncementPolicy.shouldReannounce(
                announcedWidthMargin, announcedHeightMargin, getWidthMargin(), getHeightMargin()
            )
        ) {
            AppLog.i(
                "[UI_DEBUG] CarScreen: margins drifted from the announced " +
                    "${announcedWidthMargin}x${announcedHeightMargin} to ${getWidthMargin()}x${getHeightMargin()}"
            )
            notifyingMarginDivergence = true
            try {
                onMarginsDiverged?.invoke()
            } finally {
                notifyingMarginDivergence = false
            }
        }
    }

    fun getAdjustedHeight(): Int = ProjectionGeometryPolicy.adjustedHeight(getNegotiatedHeight(), scaleFactor)

    fun getAdjustedWidth(): Int = ProjectionGeometryPolicy.adjustedWidth(getNegotiatedWidth(), scaleFactor)

    // COVER target size for the legacy forcedScale/SurfaceView path, which sizes the view through
    // LayoutParams rather than a View.scale transform.
    fun getCoverWidth(): Int =
        ProjectionGeometryPolicy.coverWidth(screenWidthPx, screenHeightPx, getNegotiatedWidth(), getNegotiatedHeight())

    fun getCoverHeight(): Int =
        ProjectionGeometryPolicy.coverHeight(screenWidthPx, screenHeightPx, getNegotiatedWidth(), getNegotiatedHeight())

    fun getNegotiatedHeight(): Int {
        val resString = negotiatedResolutionType.toString().replace("_", "")
        return try {
            resString.split("x")[1].toInt()
        } catch (e: Exception) {
            480
        }
    }

    private fun canNegotiateHevcHighResolution(): Boolean {
        if (VideoDecoder.isHevcSupported()) return true
        if (currentSettings.videoCodec != VideoDecoder.CodecType.H265.settingsValue || !currentSettings.forceSoftwareDecoding) return false
        return when (currentSettings.softwareVideoDecoder) {
            Settings.SoftwareVideoDecoder.BUNDLED_FFMPEG -> VideoDecoder.isBundledHevcDecoderAvailable()
            Settings.SoftwareVideoDecoder.DEVICE_MEDIACODEC -> VideoDecoder.isHevcDecoderAvailable(includeSoftware = true)
        }
    }

    fun getNegotiatedWidth(): Int {
        val resString = negotiatedResolutionType.toString().replace("_", "")
        return try {
            resString.split("x")[0].toInt()
        } catch (e: Exception) {
            800
        }
    }

    fun getHeightMargin(): Int =
        ProjectionGeometryPolicy.heightMargin(getNegotiatedHeight(), screenHeightPx, scaleFactor)

    fun getWidthMargin(): Int =
        ProjectionGeometryPolicy.widthMargin(getNegotiatedWidth(), screenWidthPx, scaleFactor)

    fun getScaleX(): Float = ProjectionGeometryPolicy.scaleX(
        videoFitMode, forcedScale,
        screenWidthPx, screenHeightPx, getNegotiatedWidth(), getNegotiatedHeight(),
        getWidthMargin(), getHeightMargin()
    )

    fun getScaleY(): Float = ProjectionGeometryPolicy.scaleY(
        videoFitMode, forcedScale,
        screenWidthPx, screenHeightPx, getNegotiatedWidth(), getNegotiatedHeight(),
        getWidthMargin(), getHeightMargin()
    )

    fun getDensityDpi(): Int {
        return if (this::currentSettings.isInitialized && currentSettings.dpiPixelDensity != 0) {
            currentSettings.dpiPixelDensity
        } else {
            densityDpi
        }
    }

    fun getPixelAspectRatioE4(): Int {
        // The settings row normalises anything <= 0 to 10000, so 10000 is also "unset" and is what
        // lets the derived value through. An explicit non-square choice always wins.
        val manual = if (this::currentSettings.isInitialized) currentSettings.pixelAspectRatioE4 else 0
        if (manual > 0 && manual != ProjectionGeometryPolicy.SQUARE_PIXELS_E4) return manual
        return ProjectionGeometryPolicy.pixelAspectRatioE4(
            videoFitMode, screenWidthPx, screenHeightPx, getNegotiatedWidth(), getNegotiatedHeight(),
            getWidthMargin(), getHeightMargin()
        )
    }

    fun getUsableWidth(): Int = screenWidthPx
    fun getUsableHeight(): Int = screenHeightPx

    // These are half the total margin, distributed symmetrically.
    fun getLeftMargin(): Int = getWidthMargin() / 2
    fun getRightMargin(): Int = getWidthMargin() - getLeftMargin()
    fun getTopMargin(): Int = getHeightMargin() / 2
    fun getBottomMargin(): Int = getHeightMargin() - getTopMargin()

    /**
     * Called when the actual rendering surface dimensions become known (from onSurfaceChanged).
     * Compares with the current usable area and updates the anchor if they differ.
     * @return true if the dimensions changed and margins need to be re-sent to AA.
     */
    fun updateSurfaceDimensions(surfaceW: Int, surfaceH: Int): Boolean {
        val finalSurfaceW: Int
        val finalSurfaceH: Int

        val screenOrientation = if (this::currentSettings.isInitialized) currentSettings.screenOrientation else Settings.ScreenOrientation.SYSTEM
        if (screenOrientation == Settings.ScreenOrientation.LANDSCAPE ||
            screenOrientation == Settings.ScreenOrientation.LANDSCAPE_REVERSE) {
            finalSurfaceW = Math.max(surfaceW, surfaceH)
            finalSurfaceH = Math.min(surfaceW, surfaceH)
        } else if (screenOrientation == Settings.ScreenOrientation.PORTRAIT ||
                   screenOrientation == Settings.ScreenOrientation.PORTRAIT_REVERSE) {
            finalSurfaceW = Math.min(surfaceW, surfaceH)
            finalSurfaceH = Math.max(surfaceW, surfaceH)
        } else {
            finalSurfaceW = surfaceW
            finalSurfaceH = surfaceH
        }

        val diffW = kotlin.math.abs(finalSurfaceW - screenWidthPx)
        val diffH = kotlin.math.abs(finalSurfaceH - screenHeightPx)

        if (diffW <= SURFACE_MISMATCH_TOLERANCE && diffH <= SURFACE_MISMATCH_TOLERANCE) {
            return false
        }

        if( (diffW > 0 && getNegotiatedWidth() == finalSurfaceW) || (diffH > 0 && getNegotiatedHeight() == finalSurfaceH)) {
            AppLog.i("[UI_DEBUG_FIX] Surface mismatch detected but matches negotiated resolution. Usable: ${screenWidthPx}x${screenHeightPx}, Actual surface: ${finalSurfaceW}x${finalSurfaceH}. Ignoring.")
            return false
        }

        AppLog.i("[UI_DEBUG_FIX] Surface mismatch detected! Usable: ${screenWidthPx}x${screenHeightPx}, Actual surface: ${finalSurfaceW}x${finalSurfaceH} (diff: ${diffW}x${diffH})")

        // Update anchor: the surface dimensions ARE the real usable area,
        // so the anchor is the usable area plus insets.
        realScreenWidthPx = finalSurfaceW + systemInsetLeft + systemInsetRight
        realScreenHeightPx = finalSurfaceH + systemInsetTop + systemInsetBottom

        recalculate()

        AppLog.i("[UI_DEBUG_FIX] Recalculated: usable=${screenWidthPx}x${screenHeightPx}, margins: w=${getWidthMargin()}, h=${getHeightMargin()}, per-side: L=${getLeftMargin()} T=${getTopMargin()} R=${getRightMargin()} B=${getBottomMargin()}")
        return true
    }

    /**
     * Computes a hash of all settings that affect screen dimensions.
     * Used to invalidate the cached surface dimensions when settings change.
     */
    fun computeSettingsHash(settings: Settings): Int {
        var hash = 17
        hash = 31 * hash + settings.resolutionId
        hash = 31 * hash + settings.dpiPixelDensity
        hash = 31 * hash + settings.pixelAspectRatioE4
        hash = 31 * hash + settings.insetLeft
        hash = 31 * hash + settings.insetTop
        hash = 31 * hash + settings.insetRight
        hash = 31 * hash + settings.insetBottom
        hash = 31 * hash + settings.viewMode.ordinal
        hash = 31 * hash + settings.screenOrientation.ordinal
        hash = 31 * hash + settings.fullscreenMode.value
        hash = 31 * hash + settings.videoFitMode.value
        hash = 31 * hash + (if (settings.forcedScale) 1 else 0)
        // Include physical dimensions in the hash. If the screen rotates or a foldable is unfolded,
        // the hash will change, triggering a clean unlock and recalculation.
        hash = 31 * hash + realScreenWidthPx
        hash = 31 * hash + realScreenHeightPx
        return hash
    }

    fun lockResolution() {
        if (!isResolutionLocked) {
            AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Locking resolution at $negotiatedResolutionType")
            isResolutionLocked = true
        }
    }

    fun unlockResolution() {
        if (isResolutionLocked) {
            AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Unlocking resolution (was $negotiatedResolutionType)")
            isResolutionLocked = false
        }
    }

    private const val SURFACE_MISMATCH_TOLERANCE = 4
}
