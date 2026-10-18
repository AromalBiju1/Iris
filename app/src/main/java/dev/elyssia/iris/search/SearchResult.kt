package dev.elyssia.iris.search

enum class Engine { SEARXNG, DUCKDUCKGO }

data class SearchResult(
    val url: String,
    val title: String,
    val snippet: String,
    val engine: Engine,
)

data class SearchOutcome(val engine: Engine, val results: List<SearchResult>)

class ProviderFailure(
    val providerEngine: Engine,
    cause: Throwable,
) : Exception("provider ${providerEngine.name} failed: ${cause.message}", cause)

class NoResultsException(
    val errors: List<Throwable>,
) : Exception("all providers failed (${errors.size} errors)")
