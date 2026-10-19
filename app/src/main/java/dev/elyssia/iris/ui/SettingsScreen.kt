package dev.elyssia.iris.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.elyssia.iris.browser.AdBlocker
import dev.elyssia.iris.browser.CookieCleaner
import dev.elyssia.iris.browser.PrivacyWebView
import dev.elyssia.iris.search.SearchSettings
import dev.elyssia.iris.storage.LocalStore

@Composable
fun SettingsScreen(store: LocalStore, onBack: () -> Unit) {
    val settings = remember(store) { SearchSettings(store) }
    var fallback by remember { mutableStateOf(settings.ddgFallbackEnabled()) }
    var endpoints by remember { mutableStateOf(settings.candidateUrls().joinToString("\n")) }
    var savedMsg by remember { mutableStateOf<String?>(null) }
    var confirmData by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(20.dp))
        Text("Search", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Automatic DuckDuckGo fallback")
                Text(
                    "When off, failed SearXNG searches are never sent to DuckDuckGo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = fallback, onCheckedChange = {
                fallback = it
                settings.setDdgFallbackEnabled(it)
            })
        }

        Spacer(Modifier.height(16.dp))
        Text("SearXNG endpoints", style = MaterialTheme.typography.titleMedium)
        Text(
            "One URL per line. Highest first. HTTP allowed only for these hosts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedTextField(
            value = endpoints,
            onValueChange = { endpoints = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 3,
        )
        OutlinedButton(onClick = {
            settings.setCandidateUrls(
                endpoints.lines().map { it.trim() }.filter { it.isNotBlank() },
            )
            savedMsg = "saved"
        }) { Text("Save endpoints") }
        if (savedMsg != null) {
            Text(savedMsg!!, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(16.dp))
        Text("Privacy — clear data", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { confirmData = true }) {
            Text("Clear cookies, cache & history")
        }
        Text(
            "Bookmarks are never cleared here. Private tabs are isolated" +
                if (PrivacyWebView.supportsPrivateIsolation()) " fully." else " best-effort on this WebView version.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text(
            "Iris — privacy-first hybrid browser. No accounts, no telemetry, no ad SDKs. " +
                "Search runs on your own SearXNG first. Built-in ad/tracker blocking: " +
                AdBlocker.rulesCount() + " rules, applied per page load.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (confirmData) {
        AlertDialog(
            onDismissRequest = { confirmData = false },
            title = { Text("Clear browsing data?") },
            text = { Text("Cookies, cache, history and the open-tab session are deleted. Bookmarks are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmData = false
                    CookieCleaner.clear()
                    store.clearHistory()
                    store.clearTabSession()
                    PrivacyWebView.deletePrivateProfile()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmData = false }) { Text("Cancel") } },
        )
    }
}
