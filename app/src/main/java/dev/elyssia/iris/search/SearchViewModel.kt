package dev.elyssia.iris.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.elyssia.iris.storage.LocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

sealed interface SearchUiState {
    object Idle : SearchUiState
    data class Loading(val query: String) : SearchUiState
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
    store: LocalStore,
    routerFactory: (List<String>) -> SearchRouter = ::defaultRouter,
) : ViewModel() {
    private val router: SearchRouter = routerFactory(SearchSettings(store).candidateUrls())
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    fun submit(query: String) {
        if (query.isBlank()) return
        _uiState.value = SearchUiState.Loading(query)
        viewModelScope.launch {
            try {
                val outcome = router.search(query)
                _uiState.value = SearchUiState.Results(outcome.engine, outcome.results)
            } catch (e: NoResultsException) {
                _uiState.value = SearchUiState.Error(
                    "search unavailable — " + (e.errors.lastOrNull()?.message ?: "no providers reachable"),
                    query,
                )
            } catch (t: Throwable) {
                _uiState.value = SearchUiState.Error(t.message ?: "unexpected error", query)
            }
        }
    }
}
