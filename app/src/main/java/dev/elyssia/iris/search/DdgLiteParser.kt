package dev.elyssia.iris.search

import org.jsoup.Jsoup
import java.net.URLDecoder

object DdgLiteParser {

    fun parse(html: String, limit: Int = 30): List<SearchResult> {
        val doc = Jsoup.parse(html, "https://duckduckgo.com/")
        val anchors = doc.select("a.result-link")
        val snippets = doc.select("td.result-snippet")
        return anchors.indices.take(limit).mapNotNull { i ->
            val a = anchors[i]
            val url = unwrapRedirection(a.absUrl("href"))
            if (!url.startsWith("http")) return@mapNotNull null
            SearchResult(
                url = url,
                title = a.text().ifBlank { url },
                snippet = snippets.getOrNull(i)?.text().orEmpty(),
                engine = Engine.DUCKDUCKGO,
            )
        }
    }

    // Lite anchors are href="//duckduckgo.com/l/?uddg=<urlencoded>&rut=…" wrappers.
    private fun unwrapRedirection(url: String): String {
        val marker = "duckduckgo.com/l/?"
        val idx = url.indexOf(marker)
        if (idx < 0) return url
        val query = url.substring(idx + marker.length)
        val uddg = query.split('&')
            .firstOrNull { it.startsWith("uddg=") }
            ?.removePrefix("uddg=")
            ?: return url
        return URLDecoder.decode(uddg, Charsets.UTF_8)
    }
}
