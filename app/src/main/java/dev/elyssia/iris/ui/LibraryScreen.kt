package dev.elyssia.iris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.elyssia.iris.search.faviconTile
import dev.elyssia.iris.storage.LocalStore

@Composable
fun LibraryScreen(store: LocalStore, onBack: () -> Unit) {
    var section by remember { mutableStateOf(0) } // 0 = bookmarks, 1 = history
    var bookmarks by remember { mutableStateOf(store.listBookmarks()) }
    var history by remember { mutableStateOf(store.listHistory()) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
            Text("Library", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Row {
                FilterChip(
                    selected = section == 0,
                    onClick = { section = 0 },
                    label = { Text("Bookmarks") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = section == 1,
                    onClick = { section = 1 },
                    label = { Text("History") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (section == 0) {
            if (bookmarks.isEmpty()) {
                Text("No bookmarks yet — save pages with the star or menu while browsing.")
            }
            LazyColumn {
                items(bookmarks, key = { it.url }) { b ->
                    EntryRow(
                        title = b.title.ifBlank { b.url },
                        url = b.url,
                        onDelete = {
                            store.deleteBookmark(b.url)
                            bookmarks = store.listBookmarks()
                        },
                        onClick = null,
                    )
                }
            }
        } else {
            OutlinedButton(onClick = {
                store.clearHistory()
                history = store.listHistory()
            }) { Text("Clear all history") }
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) Text("History is empty. Private browsing is never recorded.")
            LazyColumn {
                items(history, key = { it.url }) { h ->
                    EntryRow(
                        title = h.title.ifBlank { h.url },
                        url = h.url,
                        onDelete = null,
                        onClick = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(title: String, url: String, onDelete: (() -> Unit)?, onClick: (() -> Unit)?) {
    val tile = faviconTile(url)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(tile.color)),
            contentAlignment = Alignment.Center,
        ) { Text(tile.letter, color = Color.White, fontSize = 16.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "delete") }
        }
    }
}
