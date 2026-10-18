package dev.elyssia.iris.search

import kotlinx.serialization.Serializable

@Serializable
data class SearxngResponse(val results: List<SearxngResult> = emptyList())

@Serializable
data class SearxngResult(
    val url: String,
    val title: String = "",
    val content: String = "",
    val engine: String = "",
    val img_src: String? = null,
) {
    fun toSearchResult() = SearchResult(
        url = url,
        title = title.ifBlank { url },
        snippet = content,
        engine = Engine.SEARXNG,
    )
}
