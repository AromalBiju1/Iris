package dev.elyssia.iris.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SearXngProvider(
    private val client: OkHttpClient,
    private val urlProvider: () -> List<String>,
) : SearchProvider {

    override val engine: Engine = Engine.SEARXNG

    override suspend fun probe(timeoutMs: Long): Boolean {
        val urls = urlProvider()
        if (urls.isEmpty()) return false
        val probeClient = client.newBuilder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        return withContext(Dispatchers.IO) {
            urls.any { url ->
                try {
                    probeClient.newCall(Request.Builder().url(url + PROBE).build())
                        .execute().use { it.code in 200..299 }
                } catch (_: Exception) { false }
            }
        }
    }

    override suspend fun search(query: String, limit: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val urls = urlProvider().ifEmpty { listOf(DEFAULT_URL) }
        val searchClient = client.newBuilder()
            .readTimeout(4_000L, TimeUnit.MILLISECONDS)
            .build()
        var lastFailure: ProviderFailure? = null
        for (url in urls) {
            try {
                val http = url + "/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=json"
                searchClient.newCall(Request.Builder().url(http).build())
                    .execute().use { r ->
                        if (!r.isSuccessful) {
                            throw ProviderFailure(engine, IllegalStateException("HTTP ${r.code}"))
                        }
                        val body = r.body?.string()
                            ?: throw ProviderFailure(engine, IllegalStateException("empty body"))
                        json.decodeFromString<SearxngResponse>(body)
                            .results
                            .take(limit)
                            .map { it.toSearchResult() }
                    }
            } catch (e: Exception) {
                lastFailure = ProviderFailure(engine, e)
            }
        }
        throw lastFailure ?: ProviderFailure(engine, IllegalStateException("no urls attempted"))
    }

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:8888"
        private const val PROBE = "/search?q=x&format=json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
