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
                        throw ProviderFailureException(engine, cause = IllegalStateException("HTTP ${r.code}"))
                    }
                    val body = r.body?.string()
                        ?: throw ProviderFailureException(engine, cause = IllegalStateException("empty body"))
                    val results = DdgLiteParser.parse(body, limit)
                    // A challenge page parses as zero results — classify it as a
                    // failure, never as "no results found" (spec §5.2).
                    if (results.isEmpty() && looksLikeBotChallenge(body)) {
                        throw ProviderFailureException(
                            engine,
                            cause = IllegalStateException("blocked by bot challenge"),
                        )
                    }
                    results
                }
        } catch (e: Exception) {
            throw ProviderFailureException(engine, cause = e)
        }
    }

    private fun looksLikeBotChallenge(html: String): Boolean {
        val lower = html.lowercase()
        return listOf(
            "unusual traffic",
            "are you a robot",
            "verify you are",
            "/sorry/",
            "anomaly",
        ).any { it in lower }
    }

    companion object {
        const val BASE = "https://lite.duckduckgo.com/lite/"
        const val UA = "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0"
    }
}
