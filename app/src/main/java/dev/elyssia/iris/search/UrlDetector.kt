package dev.elyssia.iris.search

object UrlDetector {
    private val WITH_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+$")
    private val DOTTED = Regex("^\\S+\\.\\S+$")

    fun detect(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (WITH_SCHEME.matches(trimmed)) return trimmed
        return if (DOTTED.matches(trimmed)) "https://" + trimmed.trimEnd('/') else null
    }
}
