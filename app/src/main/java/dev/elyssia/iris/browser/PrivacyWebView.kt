package dev.elyssia.iris.browser

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object PrivacyWebView {
    val DNT_HEADERS: Map<String, String> = mapOf("DNT" to "1")

    fun configure(wv: WebView, incognito: Boolean) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = !incognito
            databaseEnabled = false
            cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
    }

    fun load(wv: WebView, url: String) = wv.loadUrl(url, DNT_HEADERS)

    fun clearAll(wv: WebView, incognito: Boolean) {
        wv.apply {
            clearCache(true)
            clearFormData()
            clearSslPreferences()
        }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
