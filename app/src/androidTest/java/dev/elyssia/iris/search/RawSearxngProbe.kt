package dev.elyssia.iris.search

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class RawSearxngProbe {
    @Test
    fun httpProbe() = runBlocking {
        try {
            val r = OkHttpClient().newCall(
                Request.Builder().url("http://127.0.0.1:8888/search?q=x&format=json").build()
            ).execute()
            assertEquals("raw http probe", 200, r.code)
        } catch (t: Throwable) {
            throw AssertionError("raw probe failed: ${t::class.simpleName}: ${t.message}", t)
        }
    }
}
