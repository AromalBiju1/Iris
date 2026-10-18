package dev.elyssia.iris.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun BrowserScreen(
    initialUrl: String,
    incognito: Boolean = false,
    onExit: () -> Unit,
    onUrlRequested: (String) -> Unit,
) {
    val tabs = remember { TabManager() }
    val webViews = remember { mutableStateMapOf<Int, WebView>() }
    var activeId by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        val t = tabs.newTab(initialUrl)
        activeId = t.id
    }

    val activeTab = tabs.tabs.firstOrNull { it.id == activeId }
    var input by remember { mutableStateOf(initialUrl) }

    Surface(Modifier.fillMaxSize()) {
        Column {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { onUrlRequested(input) }) { Text("Go") }
                },
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                tabs.tabs.forEach { t ->
                    TextButton(onClick = {
                        tabs.select(t)
                        activeId = t.id
                        input = t.url ?: ""
                        val wv = webViews[activeId]
                        val u = t.url
                        if (wv != null && u != null) PrivacyWebView.load(wv, u)
                    }) { Text(if (t.id == activeId) "\u25B6 " + t.title else t.title) }
                    IconButton(onClick = {
                        webViews.remove(t.id)?.let { wv ->
                            PrivacyWebView.clearAll(wv, incognito)
                            wv.destroy()
                        }
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
                        webViews[activeId] = wv
                        activeTab?.url?.let { PrivacyWebView.load(wv, it) }
                    }
                },
                update = { },
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { PrivacyWebView.clearAll(it, incognito) }
        }
    }
}
