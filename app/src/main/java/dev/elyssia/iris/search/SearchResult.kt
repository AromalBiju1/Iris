package dev.elyssia.iris.search

enum class Engine { SEARXNG, DUCKDUCKGO }

data class SearchResult(
    val url: String,
    val title: String,
    val snippet: String,
    val engine: Engine,
)

data class SearchOutcome(val engine: Engine, val results: List<SearchResult>)

/** One provider's non-productive attempt, with the empty-vs-failure distinction (§5.2). */
data class RouterFailure(
    val engine: Engine,
    val isEmpty: Boolean = false,
    val cause: Throwable?,
) {
    fun message(): String = if (isEmpty) {
        "${engine.name} responded with zero results"
    } else {
        "provider ${engine.name} failed: ${cause?.message ?: cause?.javaClass?.simpleName ?: "unknown"}"
    }
}

/**
 * What a provider throws inside search(); the router converts it to a
 * RouterFailure, preserving the empty-vs-outage distinction (§5.2).
 */
class ProviderFailureException(
    val engine: Engine,
    val isEmpty: Boolean = false,
    override val cause: Throwable? = null,
) : Exception(
    if (isEmpty) {
        "${engine.name} responded with zero results"
    } else {
        "provider ${engine.name} failed: ${cause?.message ?: cause?.javaClass?.simpleName ?: "unknown"}"
    }
)

class NoResultsException(
    val failures: List<RouterFailure>,
    val isGenuineEmpty: Boolean,
) : Exception(
    when {
        failures.isEmpty() -> "no providers reachable"
        isGenuineEmpty -> "no results found"
        else -> failures.joinToString(" | ") { it.message() }
    }
)
