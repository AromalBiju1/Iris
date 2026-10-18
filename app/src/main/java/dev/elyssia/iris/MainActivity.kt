package dev.elyssia.iris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.elyssia.iris.browser.BrowserScreen
import dev.elyssia.iris.browser.ConnectivityHelper
import dev.elyssia.iris.search.SearchViewModel
import dev.elyssia.iris.search.SearchSettings
import dev.elyssia.iris.search.Shortcuts
import dev.elyssia.iris.search.UrlDetector
import dev.elyssia.iris.storage.LocalStore
import dev.elyssia.iris.ui.IrisTheme
import dev.elyssia.iris.ui.LibraryScreen
import dev.elyssia.iris.ui.SearchScreen
import dev.elyssia.iris.ui.SettingsScreen
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(applicationContext)
        setContent { IrisTheme { AppRoot(store, applicationContext) } }
    }
}

/** Resolve input to a browsable URL (shortcuts first, then URL detection). */
private fun resolveInput(input: String, searxBase: String?): String? =
    Shortcuts.resolve(input, searxBase) ?: UrlDetector.detect(input)

private fun searxSearch(query: String, searxBase: String?): String? {
    val base = searxBase ?: return null
    return base + "/search?q=" + URLEncoder.encode(query, "UTF-8")
}

@Composable
fun AppRoot(store: LocalStore, appContext: android.content.Context) {
    val vm: SearchViewModel = viewModel(
        initializer = {
            SearchViewModel(store, isOnline = { ConnectivityHelper.current(appContext) })
        },
    )
    val state by vm.uiState.collectAsState()
    var browserOpen by remember { mutableStateOf(false) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var browserPrivate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var searchKey by remember { mutableStateOf(0) }

    fun openBrowser(url: String?, isPrivate: Boolean) {
        browserUrl = url
        browserPrivate = isPrivate
        browserOpen = true
    }

    fun searxBase(): String? = SearchSettings(store).candidateUrls().firstOrNull()

    val submitSearch: (String) -> Unit = { text ->
        val direct = resolveInput(text, searxBase())
        if (direct != null) {
            openBrowser(direct, false)
        } else {
            vm.submit(text)
        }
        searchKey++
    }

    if (showSettings) {
        SettingsScreen(store = store, onBack = { showSettings = false })
    } else if (showLibrary) {
        LibraryScreen(store = store, onBack = { showLibrary = false })
    } else if (browserOpen) {
        BrowserScreen(
            initialUrl = browserUrl,
            initialPrivate = browserPrivate,
            onExit = {
                browserOpen = false
                searchKey++
            },
            onUrlRequested = submitSearch,
            onOpenSettings = { showSettings = true },
            store = store,
        )
    } else {
        key(searchKey) {
            SearchScreen(
                store = store,
                uiState = state,
                onQuerySubmit = submitSearch,
                onOpenUrl = { openBrowser(it, false) },
                onOpenPrivate = { openBrowser(null, true) },
                onOpenSettings = { showSettings = true },
                onOpenLibrary = { showLibrary = true },
                onBackToIdle = { vm.clearResults() },
            )
        }
    }
}
