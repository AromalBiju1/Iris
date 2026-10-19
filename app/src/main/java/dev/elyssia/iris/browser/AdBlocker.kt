package dev.elyssia.iris.browser

/**
 * Lean in-process request blocking (spec §8 privacy leanings): major ad /
 * tracker host suffixes. No third-party SDKs, no remote blocklist fetch —
 * the list ships with the binary and can grow with app updates.
 */
object AdBlocker {
    private val blockedSuffixes = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googletagservices.com",
        "googleadservices.com",
        "google-analytics.com",
        "analytics.google.com",
        "adservice.google.com",
        "pagead.l.google.com",
        "scorecardresearch.com",
        "quantserve.com",
        "quantcount.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "adsrvr.org",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "smartadserver.com",
        "zedo.com",
        "adform.net",
        "teads.tv",
        "media.net",
        "moatads.com",
        "moatpixel.com",
        "bidswitch.net",
        "yieldmo.com",
        "sharethrough.com",
        "spotxchange.com",
        "indexww.com",
        "ads-twitter.com",
        "advertising.com",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "chartboost.com",
        "vungle.com",
        "inmobi.com",
        "crashlytics.com",
        "sentry-cdn.com",
    )

    fun shouldBlock(url: String): Boolean {
        val host = runCatching { toHost(url) }.getOrNull()?.lowercase() ?: return false
        return blockedSuffixes.any { host == it || host.endsWith(".$it") }
    }

    private fun toHost(url: String): String {
        val stripped = url.substringAfter("://").substringBefore('/')
        val host = stripped.substringBefore(':').substringBefore('@')
        return host.removePrefix("www.")
    }

    fun rulesCount(): Int = blockedSuffixes.size
}
