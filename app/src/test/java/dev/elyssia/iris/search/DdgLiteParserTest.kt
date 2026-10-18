package dev.elyssia.iris.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DdgLiteParserTest {
    private fun fixture(): String =
        javaClass.getResourceAsStream("/fixtures/ddg_lite.html")!!
            .bufferedReader().readText()

    @Test
    fun parsesFixture() {
        val results = DdgLiteParser.parse(fixture(), limit = 30)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.url.startsWith("http") && it.engine == Engine.DUCKDUCKGO })
        assertTrue(results.all { it.title.isNotBlank() })
    }

    @Test
    fun respectsLimit() {
        val results = DdgLiteParser.parse(fixture(), limit = 2)
        assertEquals(2, results.size)
    }
}
