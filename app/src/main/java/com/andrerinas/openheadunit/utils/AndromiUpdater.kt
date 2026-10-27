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
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

/**
 * Andromi Auto: finds the newest release of the chosen channel (stable, or the development
 * pre-release), downloads its APK and hands it to the system installer.
 *
 * GitHub allows 60 API calls an hour per address without a login. Two things keep the check
 * working past that: the API answer is remembered with its ETag and an unchanged answer (304) is
 * not counted against the limit; and when the API refuses anyway, the `update.json` attached to the
 * same release is read through the plain download link, which has no such limit.
 */
object AndromiUpdater {

    private const val TAG = "AndromiUpdater"
    private const val REPO = "Andromi-Auto/andromi-auto"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    /** The installable APK of every release, as the workflow names it. */
    private const val APK_NAME = "Andromi-Auto-release.apk"
    private const val DEV_TAG = "development"

    private const val PREFS = "andromi_update"
    private const val KEY_CHANNEL = "channel"

    data class Release(
        val channel: String,
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val pageUrl: String,
        val sha256: String?,
        val notes: String,
    ) {
        val build: Int get() = AndromiUpdatePolicy.buildOfVersionCode(versionCode.toLong())
    }

    sealed class CheckResult {
        data class Found(
            val release: Release,
            val installedCode: Int,
            val verdict: AndromiUpdatePolicy.Verdict,
        ) : CheckResult()
        /** The channel has no release yet. */
        data class NoRelease(val channel: String) : CheckResult()
        data class Failed(val reason: String) : CheckResult()
    }

    val installedBuild: Int
        get() = AndromiUpdatePolicy.buildOfVersionCode(BuildConfig.VERSION_CODE.toLong())

    /** The channel the user chose; until they choose, the one this build came from. */
    fun channel(context: Context): String =
        AndromiUpdatePolicy.normalizeChannel(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CHANNEL, null) ?: BuildConfig.CHANNEL
        )

    fun setChannel(context: Context, channel: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CHANNEL, AndromiUpdatePolicy.normalizeChannel(channel)).apply()
    }

    private fun apiUrl(channel: String) =
        if (channel == AndromiUpdatePolicy.CHANNEL_STABLE) "https://api.github.com/repos/$REPO/releases/latest"
        else "https://api.github.com/repos/$REPO/releases/tags/$DEV_TAG"

    private fun mirrorUrl(channel: String) =
        if (channel == AndromiUpdatePolicy.CHANNEL_STABLE) "https://github.com/$REPO/releases/latest/download/update.json"
        else "https://github.com/$REPO/releases/download/$DEV_TAG/update.json"

    private fun pageUrl(channel: String) =
        if (channel == AndromiUpdatePolicy.CHANNEL_STABLE) "https://github.com/$REPO/releases/latest"
        else "https://github.com/$REPO/releases/tag/$DEV_TAG"

    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val channel = channel(context)
        val release = try {
            fromApi(context, channel)
        } catch (e: Exception) {
            AppLog.w("$TAG: the API check failed (${e.message}), reading update.json instead")
            null
        } ?: try {
            fromMirror(context, channel)
        } catch (e: Exception) {
            AppLog.w("$TAG: the update.json check failed: ${e.message}")
            return@withContext CheckResult.Failed(e.message ?: "network error")
        }

        if (release == null) return@withContext CheckResult.NoRelease(channel)
        val verdict = AndromiUpdatePolicy.verdict(BuildConfig.VERSION_CODE, release.versionCode)
        AppLog.i("$TAG: latest $channel ${release.versionName} (code ${release.versionCode}), " +
            "installed ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}): $verdict")
        CheckResult.Found(release, BuildConfig.VERSION_CODE, verdict)
    }

    /**
     * The API's answer, or null when it has none to give (no release, rate limit, error), which
     * sends the check to update.json. Throws only for a network failure, which does the same.
     */
    private fun fromApi(context: Context, channel: String): Release? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val conn = open(context, apiUrl(channel)).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            prefs.getString("etag_$channel", null)?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    // Unchanged since the last check, and not counted against the hourly limit.
                    val body = prefs.getString("body_$channel", null) ?: return null
                    return fromApiBody(channel, body)
                }
                HttpURLConnection.HTTP_OK -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    prefs.edit()
                        .putString("etag_$channel", conn.getHeaderField("ETag"))
                        .putString("body_$channel", body)
                        .apply()
                    return fromApiBody(channel, body)
                }
                else -> {
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

    private fun fromApiBody(channel: String, body: String): Release? {
        val json = JSONObject(body)
        val tag = json.optString("tag_name")
        val notes = json.optString("body")
        val info = AndromiUpdatePolicy.parseNotes(notes)
        val code = info.versionCode ?: AndromiUpdatePolicy.versionCodeOfOldTag(tag)
            ?: throw IllegalStateException("release $tag has no version code")
        val assets = json.optJSONArray("assets")
        val apkUrl = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name") == APK_NAME }?.optString("browser_download_url")
            ?: "https://github.com/$REPO/releases/download/${Uri.encode(tag)}/$APK_NAME"
        return Release(
            channel = channel,
            versionCode = code,
            versionName = info.versionName ?: json.optString("name").ifBlank { tag },
            apkUrl = apkUrl,
            pageUrl = json.optString("html_url").ifBlank { pageUrl(channel) },
            sha256 = info.sha256,
            notes = AndromiUpdatePolicy.readableNotes(notes),
        )
    }

    /** The update.json of the channel's release; null when there is no such release. */
    private fun fromMirror(context: Context, channel: String): Release? {
        val conn = open(context, mirrorUrl(channel)).apply { instanceFollowRedirects = true }
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (code != HttpURLConnection.HTTP_OK) throw IllegalStateException("update.json answered $code")
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            return Release(
                channel = channel,
                versionCode = o.getInt("versionCode"),
                versionName = o.getString("versionName"),
                apkUrl = o.getString("apkUrl"),
                pageUrl = pageUrl(channel),
                sha256 = o.optString("sha256").ifBlank { null },
                notes = o.optString("notes").trim(),
            )
        } finally {
            conn.disconnect()
        }
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
     * size is unknown). Redirects to GitHub's file host are followed, and the file's sha256 must
     * match the one the release publishes.
     */
    suspend fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // only ever one update kept
            val target = File(dir, "Andromi-Auto-${release.versionCode}.apk")
            val digest = MessageDigest.getInstance("SHA-256")
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
                            digest.update(buffer, 0, n)
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
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256 != null && !hash.equals(release.sha256, ignoreCase = true)) {
                    throw IllegalStateException("checksum mismatch")
                }
                AppLog.i("$TAG: downloaded ${release.versionName} ($done bytes)")
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
