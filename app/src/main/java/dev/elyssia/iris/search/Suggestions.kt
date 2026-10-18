package dev.elyssia.iris.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object SuggestParser {
    /** SearXNG /autocomplete → JSON array of strings. */
    fun parseSearxng(body: String): List<String> = try {
        Json { ignoreUnknownKeys = true }.decodeFromString<List<String>>(body)
    } catch (_: Exception) {
        emptyList()
    }

    /** DDG /ac → [{"phrase": "..."}, …]. */
    fun parseDdg(body: String): List<String> = try {
        Json { ignoreUnknownKeys = true }
            .decodeFromString<List<DdgSuggest>>(body)
            .mapNotNull { it.phrase?.takeIf { p -> p.isNotBlank() } }
    } catch (_: Exception) {
        emptyList()
    }

    @Serializable
    data class DdgSuggest(val phrase: String? = null)
}

class SuggestClient(
    private val client: OkHttpClient,
    private val searxUrlsProvider: () -> List<String>,
) {
    override fun toString() = "SuggestClient"

    suspend fun suggest(prefix: String): List<String> = withContext(Dispatchers.IO) {
        val fast = client.newBuilder().readTimeout(1_500L, TimeUnit.MILLISECONDS).build()
        for (base in searxUrlsProvider()) {
            try {
                val http = base + AUTOCOMPLETE + URLEncoder.encode(prefix, "UTF-8")
                fast.newCall(Request.Builder().url(http).build()).execute().use { r ->
                    if (r.isSuccessful) {
                        val body = r.body?.string().orEmpty()
                        val out = SuggestParser.parseSearxng(body)
                        if (out.isNotEmpty()) return@withContext out
                    }
                }
            } catch (_: Exception) { /* next */ }
        }
        try {
            val http = DDG + URLEncoder.encode(prefix, "UTF-8")
            fast.newCall(
                Request.Builder().url(http)
                    .header("User-Agent", DdgLiteProvider.UA).build()
            ).execute().use { r ->
                if (r.isSuccessful) {
                    val out = SuggestParser.parseDdg(r.body?.string().orEmpty())
                    if (out.isNotEmpty()) return@withContext out
                }
            }
        } catch (_: Exception) { /* no suggestions */ }
        emptyList()
    }

    companion object {
        private const val AUTOCOMPLETE = "/autocomplete?q="
        private const val DDG = "https://duckduckgo.com/ac/?q="
    }
}
