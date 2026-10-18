package dev.elyssia.iris

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.elyssia.iris.search.Engine
import dev.elyssia.iris.search.SearXngProvider
import dev.elyssia.iris.search.SearchRouter
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealSearxngE2E {
    @Test
    fun searxngRespondsThroughTunnel() {
        val router = SearchRouter(
            providers = listOf(
                SearXngProvider(
                    OkHttpClient(),
                    urlProvider = { listOf("http://127.0.0.1:8888") },
                ),
            ),
        )
        val outcome = runBlocking { router.search("test") }
        assertEquals(Engine.SEARXNG, outcome.engine)
        assertTrue(outcome.results.isNotEmpty())
    }
}
