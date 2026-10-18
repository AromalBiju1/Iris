package dev.elyssia.iris.search

interface SearchProvider {
    val engine: Engine
    suspend fun probe(timeoutMs: Long = 2_000L): Boolean
    suspend fun search(query: String, limit: Int = 30): List<SearchResult>
}
