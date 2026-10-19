package dev.elyssia.iris.browser

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object PrivacyWebView {
    val DNT_HEADERS: Map<String, String> = mapOf("DNT" to "1")

    const val PRIVATE_PROFILE = "iris-private"

    /**
     * Spec §8.3 "Prefer isolated WebView profiles through AndroidX WebKit
     * feature detection": report whether genuine private isolation (separate
     * cookie/site-storage profile) is available on the installed WebView.
     */
    fun supportsPrivateIsolation(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    private fun privateProfile(): Profile? = try {
        if (supportsPrivateIsolation()) {
            ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE)
        } else {
            null
        }
    } catch (_: UnsupportedOperationException) {
        null
    }

    fun configure(wv: WebView, isPrivate: Boolean, dark: Boolean = false) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        // blank regions match the theme instead of a black/white void
        wv.setBackgroundColor(if (dark) 0xFF14121F.toInt() else 0xFFFFFFFF.toInt())
        applyDarkMode(wv, dark)
        if (isPrivate) {
            try {
                if (supportsPrivateIsolation()) {
                    WebViewCompat.setProfile(wv, PRIVATE_PROFILE)
                }
            } catch (_: UnsupportedOperationException) { /* degrade; caller labels UI honestly */ }
        } else {
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
        }
    }

    /** Match in-app dark theme for page rendering (WebView 105+, API 29+). */
    fun applyDarkMode(wv: WebView, dark: Boolean) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, dark)
            }
        } catch (_: Throwable) { /* older WebView engines keep site themes */ }
    }

    fun setAcceptThirdPartyCookies(wv: WebView, accept: Boolean) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, accept)
    }

    fun load(wv: WebView, url: String) = wv.loadUrl(url, DNT_HEADERS)

    /** Load attempt for a private tab — same DNT headers. */
    fun clearPrivate(wv: WebView) {
        // Only the isolated view: page-level artifacts. The enclosing profile's
        // cookies are dropped with the profile itself when the last private
        // tab closes (see TabHost), never touching normal-mode cookies.
        wv.apply {
            clearCache(true)
            clearFormData()
            clearSslPreferences()
            clearHistory()
        }
    }

    /** Delete the isolated private profile. Requires zero live private WebViews. */
    fun deletePrivateProfile() {
        try {
            if (supportsPrivateIsolation()) {
                ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE)
            }
        } catch (_: Exception) { /* deletion may be async/unsupported; documented */ }
    }
}
