package dev.elyssia.iris.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun BrowserScreen(
    initialUrl: String,
    incognito: Boolean = false,
    onExit: () -> Unit,
    onUrlRequested: (String) -> Unit,
) {
    val tabs = remember { TabManager() }
    val loadedAt = remember { mutableStateMapOf<Int, String>() }
    var activeId by remember { mutableStateOf(-1) }
    var webViewHost by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        val t = tabs.newTab(initialUrl)
        activeId = t.id
    }

    val activeTab = tabs.tabs.firstOrNull { it.id == activeId }
    var input by remember { mutableStateOf(initialUrl) }

    Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { onUrlRequested(input) }) { Text("Go") }
                },
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
            ) {
                tabs.tabs.forEach { t ->
                    TextButton(onClick = {
                        tabs.select(t)
                        activeId = t.id
                        input = t.url ?: ""
                    }) { Text(if (t.id == activeId) "\u25B6 " + t.title else t.title) }
                    IconButton(onClick = {
                        tabs.close(t)
                        if (tabs.tabs.isEmpty()) onExit()
                    }) { Text("\u00D7") }
                }
                TextButton(onClick = {
                    val t = tabs.newTab()
                    activeId = t.id
                    input = ""
                }) { Text("+") }
            }
            TextButton(onExit, modifier = Modifier.fillMaxWidth()) { Text("Back to search") }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).also { wv ->
                        wv.webViewClient = WebViewClient()
                        PrivacyWebView.configure(wv, incognito)
                        webViewHost = wv
                    }
                },
                update = { wv ->
                    // reads activeTab/loadedAt -> re-runs when tab or URL changes;
                    // single shared WebView: switching tabs navigates it
                    val target = activeTab?.url
                    if (target != null && activeId !in loadedAt) {
                        loadedAt[activeId] = target
                        PrivacyWebView.load(wv, target)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewHost?.let { PrivacyWebView.clearAll(it, incognito) }
        }
    }
}
