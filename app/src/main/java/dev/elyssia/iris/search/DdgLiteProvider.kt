package dev.elyssia.iris.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DdgLiteProvider(private val client: OkHttpClient) : SearchProvider {
    override val engine: Engine = Engine.DUCKDUCKGO

    override suspend fun probe(timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(BASE).header("User-Agent", UA).build())
                .execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    override suspend fun search(query: String, limit: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val searchClient = client.newBuilder()
            .readTimeout(5_000L, TimeUnit.MILLISECONDS)
            .build()
        try {
            val http = BASE + "?q=" + URLEncoder.encode(query, "UTF-8")
            searchClient.newCall(Request.Builder().url(http).header("User-Agent", UA).build())
                .execute().use { r ->
                    if (!r.isSuccessful) {
                        throw ProviderFailure(engine, IllegalStateException("HTTP ${r.code}"))
                    }
                    val body = r.body?.string()
                        ?: throw ProviderFailure(engine, IllegalStateException("empty body"))
                    DdgLiteParser.parse(body, limit)
                }
        } catch (e: Exception) {
            throw ProviderFailure(engine, e)
        }
    }

    companion object {
        const val BASE = "https://lite.duckduckgo.com/lite/"
        const val UA = "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0"
    }
}
