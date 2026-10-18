package dev.elyssia.iris.search

import java.net.URLEncoder

/**
 * Spec §12 built-in search shortcuts. Shortcuts explicitly route the query to
 * the corresponding service, independent of the active search provider.
 */
data class SearchShortcut(val code: String, val label: String, val urlTemplate: String)

object Shortcuts {
    private val sanctioned = listOf(
        SearchShortcut("yt", "YouTube", "https://www.youtube.com/results?search_query=%s"),
        SearchShortcut("gh", "GitHub", "https://github.com/search?q=%s"),
        SearchShortcut("wiki", "Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search=%s"),
        SearchShortcut("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    )

    private const val CODE = "!([a-z]+)"
    private val codeRegex = Regex("^$CODE\\s+(.+)$", RegexOption.IGNORE_CASE)

    /** Parse "!code rest of query" → target url, enforcing a strict allowlist. */
    fun resolve(input: String, searxBase: String?): String? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("!")) return null
        val match = codeRegex.matchEntire(trimmed) ?: return null
        val target = when (val code = match.groupValues[1].lowercase()) {
            "sx" -> searxBase?.takeIf { it.isNotBlank() }?.trimEnd('/')
                ?.let { "$it/search?q=%s" }
            else -> sanctioned.firstOrNull { it.code == code }?.urlTemplate ?: return null
        } ?: return null
        val query = match.groupValues[2].trim()
        if (query.isEmpty()) return null
        return target.replace(
            "%s",
            URLEncoder.encode(query, "UTF-8").replace("+", "%20"),
        )
    }

    fun codes(): List<String> = sanctioned.map { it.code } + "sx"
}
