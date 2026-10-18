package dev.elyssia.iris.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.elyssia.iris.storage.LocalStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

sealed interface SearchUiState {
    object Idle : SearchUiState
    data class Loading(val query: String) : SearchUiState

    /** Device has no usable network — spec §22 "offline fallback state". */
    data class Offline(val query: String) : SearchUiState

    /** Every enabled provider answered successfully with zero results. */
    data class Empty(val query: String) : SearchUiState

    data class Results(val engine: Engine, val items: List<SearchResult>) : SearchUiState
    data class Error(val message: String, val query: String) : SearchUiState
}

fun defaultRouter(urls: List<String>): SearchRouter = SearchRouter(
    providers = listOf(
        SearXngProvider(OkHttpClient(), urlProvider = { urls }),
        DdgLiteProvider(OkHttpClient()),
    ),
)

class SearchViewModel(
    private val store: LocalStore,
    private val routerFactory: (List<String>) -> SearchRouter = ::defaultRouter,
    private val isOnline: () -> Boolean = { true },
) : ViewModel() {
    private val settings = SearchSettings(store)
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    private var router = routerFactory(settings.candidateUrls())
        .also { it.allowEngine = { engine -> engine == Engine.SEARXNG || settings.ddgFallbackEnabled() } }
    private var lastCandidates = settings.candidateUrls()

    /**
     * Spec §5.5: "Allow automatic fallback to DuckDuckGo" — SearXNG is always
     * attempted; DuckDuckGo only when the user policy allows it.
     */
    fun clearResults() {
        _uiState.value = SearchUiState.Idle
    }

    fun submit(query: String) {
        if (query.isBlank()) return
        if (!isOnline()) {
            _uiState.value = SearchUiState.Offline(query)
            return
        }
        val currentCandidates = settings.candidateUrls()
        if (currentCandidates != lastCandidates) {
            lastCandidates = currentCandidates
            router = routerFactory(currentCandidates)
                .also { it.allowEngine = { engine -> engine == Engine.SEARXNG || settings.ddgFallbackEnabled() } }
        }
        _uiState.value = SearchUiState.Loading(query)
        viewModelScope.launch {
            try {
                val outcome = router.search(query)
                _uiState.value = SearchUiState.Results(outcome.engine, outcome.results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoResultsException) {
                _uiState.value = if (e.isGenuineEmpty) {
                    SearchUiState.Empty(query)
                } else {
                    SearchUiState.Error(e.message ?: "no providers reachable", query)
                }
            } catch (t: Throwable) {
                _uiState.value = SearchUiState.Error(t.message ?: "unexpected error", query)
            }
        }
    }
}
