package dev.elyssia.iris.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.elyssia.iris.storage.LocalStore

@Composable
fun BrowserScreen(
    initialUrl: String,
    incognito: Boolean = false,
    onIncognitoChange: (Boolean) -> Unit,
    onExit: () -> Unit,
    onUrlRequested: (String) -> Unit,
    store: LocalStore,
) {
    val tabs = remember { TabManager() }
    val loadedAt = remember { mutableStateMapOf<Int, String>() }
    var activeId by remember { mutableStateOf(-1) }
    var webViewHost by remember { mutableStateOf<WebView?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    var urlBar by remember { mutableStateOf(initialUrl) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val t = tabs.newTab(initialUrl)
        activeId = t.id
    }

    val activeTab = tabs.tabs.firstOrNull { it.id == activeId }

    // reset saved-star when navigating
    LaunchedEffect(activeTab?.url) { if (activeTab?.url != null) saved = false }

    // system back: pop web history first, then back to search (not out of the app)
    BackHandler {
        val wv = webViewHost
        if (wv != null && wv.canGoBack()) wv.goBack() else onExit()
    }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // ---- top bar: back | rounded url pill | star | tabs ----
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Filled.ArrowBack, "back to search")
                    }
                    OutlinedTextField(
                        value = urlBar,
                        onValueChange = { urlBar = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(26.dp),
                        placeholder = { Text("Search or enter address", maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onUrlRequested(urlBar) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                    IconButton(
                        enabled = activeTab?.url != null,
                        onClick = {
                            val url = activeTab?.url
                            if (url != null) {
                                store.upsertBookmark(url, activeTab.title.ifBlank { url })
                                saved = true
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = if (saved) "saved" else "save to collections",
                            tint = if (saved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { drawerOpen = true }) {
                        BadgedBox(
                            badge = {
                                if (tabs.tabs.size > 1) {
                                    Badge { Text(tabs.tabs.size.toString()) }
                                }
                            },
                        ) {                         Icon(Icons.Filled.Menu, "tabs") }
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).also { wv ->
                            wv.webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    if (url != null && url != "about:blank") {
                                        // lookup at callback time: activeTab captured
                                        // at factory time can be stale (null).
                                        val current = tabs.tabs.firstOrNull { it.id == activeId }
                                        if (current != null) {
                                            tabs.retitle(current, view.title ?: url)
                                        }
                                        urlBar = url
                                        if (!incognito) store.addHistory(url, view.title ?: url)
                                    }
                                }
                            }
                            PrivacyWebView.configure(wv, incognito)
                            webViewHost = wv
                        }
                    },
                    update = { wv ->
                        val target = activeTab?.url
                        if (target != null && activeId !in loadedAt) {
                            loadedAt[activeId] = target
                            PrivacyWebView.load(wv, target)
                            urlBar = target
                        }
                    },
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
            }
        }

        // ---- right-side tab rail ----
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                modifier = Modifier.fillMaxHeight().width(310.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Tabs",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            incognitoLabel(incognito),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = { drawerOpen = false }) {
                            Icon(Icons.Filled.Close, "close tabs panel")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tabs.tabs, key = { it.id }) { t ->
                            TabRow(
                                t = t,
                                isActive = t.id == activeId,
                                onSelect = {
                                    tabs.select(t)
                                    activeId = t.id
                                    urlBar = t.url ?: ""
                                    drawerOpen = false
                                },
                                onClose = {
                                    tabs.close(t)
                                    if (tabs.tabs.isEmpty()) onExit()
                                },
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            val t = tabs.newTab()
                            activeId = t.id
                            urlBar = ""
                            drawerOpen = false
                        }) { Icon(Icons.Filled.Add, "new tab") }
                        Text("New tab", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text("Incognito", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = incognito,
                            onCheckedChange = { onIncognitoChange(it) },
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewHost?.let { PrivacyWebView.clearAll(it, incognito) }
        }
    }
}

private fun incognitoLabel(enabled: Boolean) = if (enabled) "Private" else "Standard"

@Composable
private fun TabRow(
    t: TabState,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.title.ifBlank { "New Tab" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                t.url?.let { u ->
                    Text(
                        u,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "close tab") }
        }
    }
}
