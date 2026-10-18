package dev.elyssia.iris.search

class SearchRouter(
    private val providers: List<SearchProvider>,
    private val probeTimeoutMs: Long = 2_000L,
) {
    private val degradedForSession = mutableSetOf<Engine>()
    var onError: ((Engine, Throwable) -> Unit)? = null

    /**
     * Spec §5.5 fallback policy: the host decides which providers may be
     * contacted (e.g. block automatic DuckDuckGo fallback when disabled).
     */
    var allowEngine: (Engine) -> Boolean = { true }

    suspend fun search(query: String, limit: Int = 30): SearchOutcome {
        val failures = mutableListOf<RouterFailure>()
        for (provider in providers) {
            if (provider.engine in degradedForSession) continue
            if (!allowEngine(provider.engine)) continue
            if (!provider.probe(probeTimeoutMs)) {
                failures.add(RouterFailure(provider.engine, isEmpty = false, cause = IllegalStateException("probe failed")))
                continue
            }
            try {
                val results = provider.search(query, limit)
                if (results.isNotEmpty()) {
                    return SearchOutcome(provider.engine, results)
                }
                // Success response with genuinely zero results is NOT an outage.
                failures.add(RouterFailure(provider.engine, isEmpty = true, cause = null))
            } catch (t: Throwable) {
                degradedForSession.add(provider.engine)
                onError?.invoke(provider.engine, t)
                failures.add(RouterFailure(provider.engine, isEmpty = false, cause = t))
            }
        }
        if (failures.isEmpty()) {
            throw NoResultsException(emptyList(), isGenuineEmpty = false)
        }
        throw NoResultsException(
            failures,
            isGenuineEmpty = failures.all { it.isEmpty },
        )
    }
}
