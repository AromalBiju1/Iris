package dev.elyssia.iris.search

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRouterTest {

    private fun provider(
        engine: Engine,
        probeResult: Boolean,
        results: List<SearchResult>,
        throwOnSearch: Boolean = false,
    ) = object : SearchProvider {
        override val engine = engine
        override suspend fun probe(timeoutMs: Long) = probeResult
        override suspend fun search(query: String, limit: Int): List<SearchResult> {
            if (throwOnSearch) throw ProviderFailureException(engine, cause = IllegalStateException("boom"))
            return results
        }
    }

    private fun result(engine: Engine) = SearchResult("https://x", "t", "s", engine)

    @Test
    fun primaryHealthyWins() = runTest {
        val router = SearchRouter(listOf(
            provider(Engine.SEARXNG, true, listOf(result(Engine.SEARXNG))),
            provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO))),
        ))
        assertEquals(Engine.SEARXNG, router.search("q").engine)
    }

    @Test
    fun fallsBackWhenProbeFails() = runTest {
        val router = SearchRouter(listOf(
            provider(Engine.SEARXNG, false, emptyList()),
            provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO))),
        ))
        assertEquals(Engine.DUCKDUCKGO, router.search("q").engine)
    }

    @Test
    fun fallsBackOnSearchFailure() = runTest {
        val router = SearchRouter(listOf(
            provider(Engine.SEARXNG, true, emptyList(), throwOnSearch = true),
            provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO))),
        ))
        assertEquals(Engine.DUCKDUCKGO, router.search("q").engine)
    }

    @Test
    fun emptyResultsFallThrough() = runTest {
        val router = SearchRouter(listOf(
            provider(Engine.SEARXNG, true, emptyList()),
            provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO))),
        ))
        assertEquals(Engine.DUCKDUCKGO, router.search("q").engine)
    }

    @Test
    fun allFailThrowsNoResults() = runTest {
        val router = SearchRouter(listOf(
            provider(Engine.SEARXNG, false, emptyList()),
            provider(Engine.DUCKDUCKGO, false, emptyList()),
        ))
        try {
            router.search("q")
            throw AssertionError("expected NoResultsException")
        } catch (e: NoResultsException) {
            assertEquals(2, e.failures.size)
        }
    }

    @Test
    fun searchFailureDegradesForSession() = runTest {
        var calls = 0
        val flaky = object : SearchProvider {
            override val engine = Engine.SEARXNG
            override suspend fun probe(timeoutMs: Long) = true
            override suspend fun search(query: String, limit: Int): List<SearchResult> {
                calls++
                throw ProviderFailureException(engine, cause = IllegalStateException("broken"))
            }
        }
        val router = SearchRouter(listOf(
            flaky,
            provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO))),
        ))
        router.search("a")
        router.search("b")
        assertEquals(1, calls)
    }

    @Test
    fun probeFailureDoesNotDegrade() = runTest {
        var probes = 0
        val cold = object : SearchProvider {
            override val engine = Engine.SEARXNG
            override suspend fun probe(timeoutMs: Long): Boolean { probes++; return false }
            override suspend fun search(query: String, limit: Int): List<SearchResult> {
                throw ProviderFailureException(engine, cause = IllegalStateException("should not be reached"))
            }
        }
        val router = SearchRouter(listOf(
            cold,
            provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO))),
        ))
        router.search("a")
        router.search("b")
        assertEquals(2, probes)
    }
}
