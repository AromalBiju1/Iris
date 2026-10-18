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
import dev.elyssia.iris.search.SearchViewModel
import dev.elyssia.iris.search.UrlDetector
import dev.elyssia.iris.storage.LocalStore
import dev.elyssia.iris.ui.IrisTheme
import dev.elyssia.iris.ui.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(applicationContext)
        setContent { IrisTheme { AppRoot(store) } }
    }
}

@Composable
fun AppRoot(store: LocalStore) {
    val vm: SearchViewModel = viewModel(initializer = { SearchViewModel(store) })
    val state by vm.uiState.collectAsState()
    var browsingUrl: String? by remember { mutableStateOf<String?>(null) }
    var incognito: Boolean by remember { mutableStateOf(false) }
    var searchKey by remember { mutableStateOf(0) }
    val currentUrl = browsingUrl
    if (currentUrl == null) {
        // key: refresh collections after browsing (star saves)
        key(searchKey) {
            SearchScreen(
                store = store,
                uiState = state,
                onQuerySubmit = {
                    val url = UrlDetector.detect(it)
                    if (url != null) browsingUrl = url else vm.submit(it)
                },
                onOpenUrl = { browsingUrl = it },
            )
        }
    } else {
        BrowserScreen(
            initialUrl = currentUrl,
            incognito = incognito,
            onIncognitoChange = { incognito = it },
            onExit = {
                browsingUrl = null
                searchKey++
            },
            onUrlRequested = { text ->
                val url = UrlDetector.detect(text)
                if (url != null) {
                    browsingUrl = url
                } else {
                    browsingUrl = null
                    searchKey++
                    vm.submit(text)
                }
            },
            store = store,
        )
    }
}
