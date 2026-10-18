package dev.elyssia.iris.search

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearXngParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesFixtureIntoSearchResults() {
        val body = javaClass.getResourceAsStream("/fixtures/searxng_test.json")!!
            .bufferedReader().readText()
        val response = json.decodeFromString<SearxngResponse>(body)
        assertTrue(response.results.isNotEmpty())
        val first = response.results.first()
        assertTrue(first.url.startsWith("http"))
        assertEquals(Engine.SEARXNG, first.toSearchResult().engine)
    }
}
