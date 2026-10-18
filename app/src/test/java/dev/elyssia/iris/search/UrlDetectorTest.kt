package dev.elyssia.iris.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlDetectorTest {
    @Test
    fun detectsPlainUrl() = assertEquals("https://example.org", UrlDetector.detect("example.org"))

    @Test
    fun keepsScheme() = assertEquals("http://foo.bar/baz", UrlDetector.detect("http://foo.bar/baz"))

    @Test
    fun keepsHttps() = assertEquals("https://a.b/c?q=2", UrlDetector.detect("https://a.b/c?q=2"))

    @Test
    fun searchWordsAreNull() = assertNull(UrlDetector.detect("how to cook rice"))

    @Test
    fun blankIsNull() = assertNull(UrlDetector.detect("   "))

    @Test
    fun hostWithPortCountsAsUrl() = assertEquals("https://127.0.0.1:8888", UrlDetector.detect("127.0.0.1:8888"))
}
