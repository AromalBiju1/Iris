package dev.elyssia.iris.search

import android.content.Context
import dev.elyssia.iris.storage.LocalStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabPersistenceTest {
    private lateinit var store: LocalStore

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("iris.db")
        store = LocalStore(context)
    }

    @Test fun savesAndLoadsNormalTabs() {
        store.saveTabs(listOf(
            Triple(1L, "https://a.example", "A"),
            Triple(2L, "https://b.example", "B"),
        ))
        val rows = store.loadTabs()
        assertEquals(2, rows.size)
        assertEquals("https://a.example", rows[0].url)
        assertEquals("B", rows[1].title)
        store.clearTabSession()
        assertEquals(0, store.loadTabs().size)
    }

    @Test fun routerPolicyBlocksDdgWhenDisallowed() = runTest {
        // DDG disabled → SEARXNG attempt only; second provider must never run
        val failingSe = object : SearchProvider {
            override val engine = Engine.SEARXNG
            override suspend fun probe(timeoutMs: Long) = true
            override suspend fun search(query: String, limit: Int) =
                throw ProviderFailureException(engine, cause = IllegalStateException("down"))
        }
        val ddg = object : SearchProvider {
            override val engine = Engine.DUCKDUCKGO
            override suspend fun probe(timeoutMs: Long) = true
            override suspend fun search(query: String, limit: Int): List<SearchResult> {
                throw AssertionError("DDG must be blocked by allowEngine")
            }
        }
        val router = SearchRouter(listOf(failingSe, ddg)).apply {
            allowEngine = { it == Engine.SEARXNG }
        }
        try {
            router.search("q")
            assertFalse("expected NoResultsException", true)
        } catch (e: NoResultsException) {
            assertFalse(e.isGenuineEmpty)
        }
    }

    @Test fun genuineEmptyMarkedTrue() = runTest {
        fun empty(engine: Engine) = object : SearchProvider {
            override val engine = engine
            override suspend fun probe(timeoutMs: Long) = true
            override suspend fun search(query: String, limit: Int): List<SearchResult> = emptyList()
        }
        val router = SearchRouter(listOf(empty(Engine.SEARXNG), empty(Engine.DUCKDUCKGO)))
        try {
            router.search("q")
            assertFalse("expected NoResultsException", true)
        } catch (e: NoResultsException) {
            assertTrue(e.isGenuineEmpty)
        }
    }
}
