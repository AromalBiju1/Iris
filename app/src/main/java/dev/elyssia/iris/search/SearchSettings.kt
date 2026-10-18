package dev.elyssia.iris.search

import dev.elyssia.iris.storage.LocalStore

class SearchSettings(private val store: LocalStore) {
    fun candidateUrls(): List<String> =
        store.getSetting("searx_urls")?.split("\n")
            ?.filter { it.isNotBlank() }?.map { it.trim() }
            ?: DEFAULT_CANDIDATES

    fun setCandidateUrls(urls: List<String>) = store.setSetting("searx_urls", urls.joinToString("\n"))

    companion object {
        val DEFAULT_CANDIDATES = listOf(
            "http://100.110.99.98:8888",
            "http://192.168.31.65:8888",
            "http://127.0.0.1:8888",
        )
    }
}
