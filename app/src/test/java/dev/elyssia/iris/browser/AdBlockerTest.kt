package dev.elyssia.iris.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {
    @Test fun blocksMajorAdHosts() {
        assertTrue(AdBlocker.shouldBlock("https://securepubads.g.doubleclick.net/tag/js/gpt.js"))
        assertTrue(AdBlocker.shouldBlock("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlocker.shouldBlock("https://www.google-analytics.com/analytics.js"))
        assertTrue(AdBlocker.shouldBlock("https://api.taboola.com/1x2.json"))
    }

    @Test fun passesNormalContent() {
        assertFalse(AdBlocker.shouldBlock("https://en.wikipedia.org/wiki/Iris_(plant)"))
        assertFalse(AdBlocker.shouldBlock("https://www.youtube.com/watch?v=test"))
        assertFalse(AdBlocker.shouldBlock("https://duckduckgo.com/?q=test"))
    }

    @Test fun suffixMatchIsExactBoundaried() {
        assertTrue(AdBlocker.shouldBlock("https://sub.media.net/script.js"))
        assertFalse(AdBlocker.shouldBlock("https://evildoubleclick.net/steal.js"))
        assertFalse(AdBlocker.shouldBlock(""))
    }
}
