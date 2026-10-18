package dev.elyssia.iris.search

class SearchRouter(
    private val providers: List<SearchProvider>,
    private val probeTimeoutMs: Long = 2_000L,
) {
    private val degradedForSession = mutableSetOf<Engine>()
    var onError: ((Engine, Throwable) -> Unit)? = null

    suspend fun search(query: String, limit: Int = 30): SearchOutcome {
        val errors = mutableListOf<Throwable>()
        for (provider in providers) {
            if (provider.engine in degradedForSession) continue
            if (!provider.probe(probeTimeoutMs)) {
                errors.add(ProviderFailure(provider.engine, IllegalStateException("probe failed")))
                continue
            }
            try {
                val results = provider.search(query, limit)
                if (results.isNotEmpty()) {
                    return SearchOutcome(provider.engine, results)
                }
                errors.add(ProviderFailure(provider.engine, IllegalStateException("empty results")))
            } catch (t: Throwable) {
                degradedForSession.add(provider.engine)
                onError?.invoke(provider.engine, t)
                errors.add(ProviderFailure(provider.engine, t))
            }
        }
        throw NoResultsException(errors)
    }
}
