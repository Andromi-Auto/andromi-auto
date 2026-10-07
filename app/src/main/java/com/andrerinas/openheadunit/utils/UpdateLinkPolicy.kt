package com.andrerinas.openheadunit.utils

/**
 * Chooses the link "Check for updates" opens. A browser too old for TLS 1.2 also cannot run the
 * script that lists a release page's assets, so those units get the APK link itself.
 */
object UpdateLinkPolicy {

    /** The release APK's download URL from (name, url) asset pairs, skipping the debug build. */
    fun pickApk(assets: List<Pair<String, String>>): String? {
        for ((name, url) in assets) {
            if (name.endsWith(".apk") && !name.endsWith("_debug.apk") && url.isNotEmpty()) return url
        }
        return null
    }

    fun linkToOpen(releaseUrl: String, apkUrl: String?, needsDirectLink: Boolean): String =
        if (needsDirectLink && apkUrl != null) apkUrl else releaseUrl
}
