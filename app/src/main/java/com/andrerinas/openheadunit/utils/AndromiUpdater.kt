package com.andrerinas.openheadunit.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.andrerinas.openheadunit.BuildConfig
import com.andrerinas.openheadunit.ssl.ConscryptInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

/**
 * Andromi Auto: checks this repository's latest **stable** release (pre-releases such as the
 * development build are never offered), downloads its APK and hands it to the system installer.
 *
 * GitHub allows 60 API calls an hour per address without a login. Two things keep the check
 * working past that: the API answer's ETag is remembered and sent back, and an unchanged answer
 * (304) is not counted against the limit; and when the API refuses anyway, the tag is read from
 * where the web page `github.com/<repo>/releases/latest` redirects, which has no such limit.
 */
object AndromiUpdater {

    private const val TAG = "AndromiUpdater"
    private const val REPO = "Andromi-Auto/andromi-auto"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val WEB_LATEST = "https://github.com/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    /** The installable APK of a stable release, as the workflow names it. */
    private const val APK_NAME = "Andromi-Auto-release.apk"

    private const val PREFS = "andromi_update"
    private const val KEY_ETAG = "etag"
    private const val KEY_TAG = "tag"
    private const val KEY_PAGE = "page"

    data class StableRelease(val tag: String, val build: Int, val pageUrl: String, val apkUrl: String)

    sealed class CheckResult {
        data class Found(
            val release: StableRelease,
            val installedBuild: Int,
            val verdict: AndromiUpdatePolicy.Verdict,
        ) : CheckResult()
        /** No stable release has been published yet. */
        object NoStableRelease : CheckResult()
        data class Failed(val reason: String) : CheckResult()
    }

    val installedBuild: Int
        get() = AndromiUpdatePolicy.buildOfVersionCode(BuildConfig.VERSION_CODE.toLong())

    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val release = try {
            fromApi(context)
        } catch (e: Exception) {
            AppLog.w("$TAG: the API check failed (${e.message}), asking the releases page instead")
            null
        } ?: try {
            fromWebRedirect(context)
        } catch (e: Exception) {
            AppLog.w("$TAG: the releases page check failed: ${e.message}")
            return@withContext CheckResult.Failed(e.message ?: "network error")
        }

        if (release == null) return@withContext CheckResult.NoStableRelease
        val verdict = AndromiUpdatePolicy.verdict(installedBuild, release.build)
        AppLog.i("$TAG: latest stable ${release.tag} (build ${release.build}), installed build $installedBuild: $verdict")
        CheckResult.Found(release, installedBuild, verdict)
    }

    /**
     * The API's answer, or null when it has none to give (rate limit, error), which sends the
     * check to the web page. Throws only for a network failure, which does the same.
     */
    private fun fromApi(context: Context): StableRelease? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val conn = open(context, API_LATEST).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            prefs.getString(KEY_ETAG, null)?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    // Unchanged since the last check, and not counted against the hourly limit.
                    val tag = prefs.getString(KEY_TAG, null) ?: return null
                    return releaseFor(tag, prefs.getString(KEY_PAGE, null))
                }
                HttpURLConnection.HTTP_OK -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val tag = json.optString("tag_name")
                    val page = json.optString("html_url").ifBlank { null }
                    prefs.edit()
                        .putString(KEY_ETAG, conn.getHeaderField("ETag"))
                        .putString(KEY_TAG, tag)
                        .putString(KEY_PAGE, page)
                        .apply()
                    return releaseFor(tag, page) ?: throw IllegalStateException("latest release $tag has no build number")
                }
                else -> {
                    // 404 (no stable release yet) also lands here: the web page then says the same.
                    val remaining = conn.getHeaderField("X-RateLimit-Remaining")
                    AppLog.w("$TAG: API answered $code" +
                        if (AndromiUpdatePolicy.isRateLimited(code, remaining)) " (hourly limit spent)" else "")
                    return null
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** The tag the web page `releases/latest` redirects to; null when there is no stable release. */
    private fun fromWebRedirect(context: Context): StableRelease? {
        val conn = open(context, WEB_LATEST).apply {
            instanceFollowRedirects = false
            requestMethod = "HEAD"
        }
        try {
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            val tag = AndromiUpdatePolicy.tagFromLatestRedirect(location)
            if (tag == null) {
                // With no stable release the page redirects to the release list instead.
                if (code in 300..399) return null
                throw IllegalStateException("releases page answered $code")
            }
            return releaseFor(tag, null) ?: throw IllegalStateException("latest release $tag has no build number")
        } finally {
            conn.disconnect()
        }
    }

    private fun releaseFor(tag: String, page: String?): StableRelease? {
        val build = AndromiUpdatePolicy.buildOfTag(tag) ?: return null
        val encoded = Uri.encode(tag)
        return StableRelease(
            tag = tag,
            build = build,
            pageUrl = page ?: "https://github.com/$REPO/releases/tag/$encoded",
            // A plain download link, which the API's limit does not cover either.
            apkUrl = "https://github.com/$REPO/releases/download/$encoded/$APK_NAME",
        )
    }

    private fun open(context: Context, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "AndromiAuto-App")
            if (this is HttpsURLConnection) {
                ConscryptInitializer.httpsSocketFactory(context)?.let { sslSocketFactory = it }
            }
        }

    /**
     * Downloads the release APK into the cache, reporting progress as 0..100 (or -1 while the
     * size is unknown). Redirects to GitHub's file host are followed.
     */
    suspend fun download(context: Context, release: StableRelease, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // only ever one update kept
            val target = File(dir, "Andromi-Auto-build${release.build}.apk")
            val conn = open(context, release.apkUrl).apply { instanceFollowRedirects = true }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("download answered ${conn.responseCode}")
                }
                val total = conn.contentLengthLong
                var done = 0L
                var lastPercent = -2
                conn.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            val percent = if (total > 0) (done * 100 / total).toInt() else -1
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) { onProgress(percent) }
                            }
                        }
                    }
                }
                if (total > 0 && done != total) throw IllegalStateException("download incomplete")
                AppLog.i("$TAG: downloaded ${release.tag} ($done bytes)")
                target
            } catch (e: Exception) {
                target.delete()
                throw e
            } finally {
                conn.disconnect()
            }
        }

    /** Whether Android lets this app open the installer; on 8.0+ the user allows it once. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the screen where the user allows Andromi Auto to install apps. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Hands the downloaded APK to the system installer, which asks the user to confirm. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            AppLog.e("$TAG: could not open $url", e)
        }
    }
}
