package dev.elyssia.iris.browser

import android.webkit.CookieManager

object CookieCleaner {
    fun clear() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
    }
}
