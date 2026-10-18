package dev.elyssia.iris.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.elyssia.iris.storage.LocalStore

private const val MAX_ACTIVE_WEBVIEWS = 3

object ConnectivityHelper {
    /** true only when the active network is validated (spec §17 state 1/2 distinction). */
    fun current(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
}

@Composable
fun BrowserScreen(
    initialUrl: String?,
    initialPrivate: Boolean = false,
    onExit: () -> Unit,
    onUrlRequested: (String) -> Unit,
    onOpenSettings: () -> Unit,
    store: LocalStore,
) {
    val tabs = remember { TabManager() }
    val holders = remember { mutableStateMapOf<Int, WebView>() }
    val loadingIds = remember { mutableStateMapOf<Int, Boolean>() }
    var activeId by remember { mutableStateOf(-1) }
    var drawerOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var urlBar by remember { mutableStateOf("") }
    var online by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // --- session restore (§16 TabSession); private tabs are never restored ---
    LaunchedEffect(Unit) {
        online = ConnectivityHelper.current(context)
        val restored = store.loadTabs()
        for (row in restored) tabs.newTab(row.url, isPrivate = false)
        val t = tabs.newTab(initialUrl, isPrivate = initialPrivate)
        activeId = t.id
        urlBar = t.url ?: ""
    }

    LaunchedEffect(activeId, tabs.tabs.size) {
        val tab = tabs.tabs.firstOrNull { it.id == activeId } ?: return@LaunchedEffect
        ensureWebView(
            tab = tab,
            tabs = tabs,
            holders = holders,
            loadingIds = loadingIds,
            context = context,
            store = store,
            onUrlChange = { if (it.id == activeId) urlBar = it.url ?: urlBar },
        )
    }

    // system back: pop web history first, then back to search
    BackHandler {
        val wv = holders[activeId]
        if (wv != null && wv.canGoBack()) wv.goBack() else onExit()
    }

    fun clearDataOnExit() {
        CookieCleaner.clear()
        holders.values.forEach { it.clearCache(true) }
        store.clearHistory()
        store.clearTabSession()
        PrivacyWebView.deletePrivateProfile()
    }

    fun closeTab(tab: TabState) {
        holders[tab.id]?.let { wv ->
            if (tab.isPrivate) PrivacyWebView.clearPrivate(wv)
            wv.destroy()
            holders.remove(tab.id)
            loadingIds.remove(tab.id)
        }
        tabs.close(tab)
        if (tab.isPrivate && tabs.tabs.none { it.isPrivate }) PrivacyWebView.deletePrivateProfile()
        persistSession(tabs, store, emptyAfterClose = tabs.tabs.isEmpty())
        if (tabs.tabs.isEmpty()) onExit()
    }

    Box(Modifier.fillMaxSize()) {
        Column {
            // ---- web content: one lazy WebView per tab within the active limit ----
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (!online) {
                    OfflineBanner(
                        onRetry = { holders[activeId]?.reload() },
                        modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
                    )
                }
                holders.forEach { (id, wv) ->
                    val isActive = id == activeId
                    if (isActive) {
                        AndroidView(
                            factory = { wv },
                            update = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // kept alive but hidden beyond the active tab
                        AndroidView(
                            factory = { wv },
                            update = {},
                            modifier = Modifier.fillMaxSize().alpha(0f),
                        )
                    }
                }
            }

            // ---- nav row (§6.2): ◀ ▶ ⟳ Tabs ⋮ ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val wv = holders[activeId]
                val loading = loadingIds[activeId] == true
                IconButton(enabled = wv?.canGoBack() == true, onClick = { wv?.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "back")
                }
                IconButton(enabled = wv?.canGoForward() == true, onClick = { wv?.goForward() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "forward")
                }
                IconButton(onClick = { if (loading) wv?.stopLoading() else wv?.reload() }) {
                    if (loading) Icon(Icons.Filled.Close, "stop") else Icon(Icons.Filled.Refresh, "reload")
                }

                IconButton(onClick = { drawerOpen = true }) {
                    BadgedBox(
                        badge = { if (tabs.tabs.size > 1) Badge { Text(tabs.tabs.size.toString()) } },
                    ) { Icon(Icons.Filled.Menu, "tabs") }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onExit) { Icon(Icons.Filled.Home, "Iris home") }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("New tab") },
                            onClick = {
                                menuOpen = false
                                val t = tabs.newTab(); activeId = t.id
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("New private tab") },
                            onClick = {
                                menuOpen = false
                                val t = tabs.newTab(isPrivate = true)
                                activeId = t.id
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Reopen closed tab") },
                            enabled = tabs.hasReopenable(),
                            onClick = {
                                menuOpen = false
                                tabs.reopenClosed()?.let { activeId = it.id }
                            },
                        )
                        DropdownMenuItem(text = { Text("Save to collections") }, onClick = {
                            menuOpen = false
                            val u = wv?.url ?: return@DropdownMenuItem
                            store.upsertBookmark(u, tabs.titleFor(tabs.active() ?: return@DropdownMenuItem))
                        })
                        DropdownMenuItem(text = { Text("Share page") }, enabled = wv?.url != null, onClick = {
                            menuOpen = false
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, wv?.url)
                            }
                            context.startActivity(Intent.createChooser(share, "Share via"))
                        })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = {
                            menuOpen = false
                            onOpenSettings()
                        })
                        DropdownMenuItem(text = { Text("Exit & clear data") }, onClick = {
                            menuOpen = false
                            confirmClear = true
                        })
                    }
                }
            }

            // ---- address row (§6.2 second row) ----
            OutlinedTextField(
                value = urlBar,
                onValueChange = { urlBar = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .navigationBarsPadding(),
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                placeholder = { Text("Search or enter address", maxLines = 1) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onUrlRequested(urlBar) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
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
                        Text("Tabs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { drawerOpen = false }) {
                            Icon(Icons.Filled.Close, "close tabs panel")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tabs.tabs.forEach { t ->
                            item(key = t.id) {
                                TabRow(
                                    t = t,
                                    isActive = t.id == activeId,
                                    onSelect = {
                                        tabs.select(t)
                                        activeId = t.id
                                        urlBar = t.url ?: ""
                                        drawerOpen = false
                                    },
                                    onClose = { closeTab(t) },
                                )
                            }
                        }
                        if (tabs.hasReopenable()) {
                            item {
                                TextButton(onClick = { tabs.reopenClosed()?.let { activeId = it.id } }) {
                                    Text("Reopen closed tab")
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val t = tabs.newTab(); activeId = t.id; urlBar = ""; drawerOpen = false
                        }) { Icon(Icons.Filled.Add, "new tab") }
                        Text("New tab", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            val t = tabs.newTab(isPrivate = true)
                            activeId = t.id; urlBar = ""; drawerOpen = false
                        }) { Text("New private tab") }
                    }
                }
            }
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Exit & clear data") },
                text = { Text("Close Iris and clear cookies, cache, history and the open-tab session? Bookmarks are kept.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClear = false
                        clearDataOnExit()
                        (context as? Activity)?.finishAffinity()
                    }) { Text("Close & clear") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                },
            )
        }
    }

    // ---- app exit: destroy all WebViews; private profile deletion (§8.3) ----
    DisposableEffect(Unit) {
        onDispose {
            val privateTabs = tabs.tabs.filter { it.isPrivate }
            privateTabs.forEach { t ->
                holders[t.id]?.let { wv ->
                    PrivacyWebView.clearPrivate(wv)
                    wv.destroy()
                }
            }
            holders.values.forEach { it.destroy() }
            holders.clear()
            if (privateTabs.isNotEmpty()) PrivacyWebView.deletePrivateProfile()
            persistSession(tabs, store)
        }
    }
}

private fun persistSession(tabs: TabManager, store: LocalStore, emptyAfterClose: Boolean = false) {
    // Private tabs are never persisted (spec §7, §8.3).
    val rows = tabs.tabs.filter { !it.isPrivate && it.url != null }
        .map { Triple(it.id.toLong(), it.url!!, it.title) }
    store.saveTabs(rows)
    if (emptyAfterClose) store.clearTabSession()
}

/**
 * Create the WebView for tab when missing; suspend the oldest inactive
 * WebView when the active limit is exceeded (§7 performance rules).
 * Reconstruction from stored navigation state = url + title only;
 * form input, JS state and scroll position are not preserved.
 */
private fun ensureWebView(
    tab: TabState,
    tabs: TabManager,
    holders: MutableMap<Int, WebView>,
    loadingIds: MutableMap<Int, Boolean>,
    context: Context,
    store: LocalStore,
    onUrlChange: (TabState) -> Unit,
) {
    if (holders.containsKey(tab.id)) return
    while (holders.size >= MAX_ACTIVE_WEBVIEWS) {
        val activeNow = tabs.active()?.id
        val victim = holders.keys.firstOrNull { it != activeNow } ?: break
        holders[victim]?.destroy()
        holders.remove(victim)
        loadingIds.remove(victim)
    }
    val wv = WebView(context)
    PrivacyWebView.configure(wv, tab.isPrivate)
    wv.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            if (url == null) return
            val current = tabs.tabs.firstOrNull { it.id == tab.id } ?: return
            tabs.openedUrl(current, url)
            loadingIds[current.id] = true
            onUrlChange(TabState(current.id, url, current.title, current.isPrivate))
            persistSession(tabs, store)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            loadingIds[tab.id] = false
            if (url == null || url.startsWith("about:")) return
            val current = tabs.tabs.firstOrNull { it.id == tab.id } ?: return
            val title = view.title ?: url
            tabs.retitle(current, title)
            tabs.openedUrl(current, url)
            // normal-mode history only; private tabs never enter records (§8.3)
            if (!current.isPrivate && url.startsWith("http")) {
                store.addHistory(url, title)
            }
            onUrlChange(TabState(current.id, url, title, current.isPrivate))
            persistSession(tabs, store)
        }
    }
    if (tab.url != null) PrivacyWebView.load(wv, tab.url)
    holders[tab.id] = wv
}

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.title.ifBlank { "New Tab" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    if (t.isPrivate) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                " PRIVATE ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.padding(2.dp),
                            )
                        }
                    }
                }
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

@Composable
private fun OfflineBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("No internet", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Pages won't load. Collections and bookmarks still work.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

