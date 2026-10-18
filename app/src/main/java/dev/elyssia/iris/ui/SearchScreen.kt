package dev.elyssia.iris.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.elyssia.iris.R
import dev.elyssia.iris.search.faviconTile
import dev.elyssia.iris.search.Engine
import dev.elyssia.iris.search.SearchResult
import dev.elyssia.iris.search.SearchUiState
import dev.elyssia.iris.search.SuggestClient
import dev.elyssia.iris.search.SearchSettings
import dev.elyssia.iris.storage.Bookmark
import dev.elyssia.iris.storage.LocalStore
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

private fun engineLabel(engine: Engine) = when (engine) {
    Engine.SEARXNG -> "SearXNG"
    Engine.DUCKDUCKGO -> "DuckDuckGo"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    store: LocalStore,
    uiState: SearchUiState,
    onQuerySubmit: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var suggester by remember { mutableStateOf<SuggestClient?>(null) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var bookmarks by remember { mutableStateOf(store.listBookmarks()) }

    LaunchedEffect(Unit) {
        suggester = SuggestClient(
            OkHttpClient(),
            searxUrlsProvider = { SearchSettings(store).candidateUrls() },
        )
    }
    LaunchedEffect(text) {
        val client = suggester ?: return@LaunchedEffect
        suggestions = emptyList()
        if (text.length >= 2) {
            delay(250)
            suggestions = runCatching { client.suggest(text) }.getOrDefault(emptyList())
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            // ---- header ----
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Iris",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // ---- centered search ----
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Search privately…") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = ""; suggestions = emptyList() }) {
                                Icon(Icons.Filled.Close, "clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { if (text.isNotBlank()) onQuerySubmit(text) },
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(0.96f),
                )
            }

            // ---- suggestions ----
            if (suggestions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Column {
                        suggestions.take(6).forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onQuerySubmit(s) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Search, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(s, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    // ---- collections ----
                    if (bookmarks.isNotEmpty()) {
                        Text(
                            "Collections",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(bookmarks, key = { it.url }) { b: Bookmark ->
                                CollectionCard(b, onOpen = { onOpenUrl(b.url) }) {
                                    store.deleteBookmark(b.url)
                                    bookmarks = store.listBookmarks()
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Spacer(Modifier.height(28.dp))
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "Iris",
                        modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally),
                    )
                    Text(
                        "Private search, zero tracking",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
                    )
                }
                is SearchUiState.Loading -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
                is SearchUiState.Error -> Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onQuerySubmit(state.query) }) { Text("Retry") }
                }
                is SearchUiState.Results -> {
                    Text(
                        "via " + engineLabel(state.engine) + " · " + state.items.size + " results",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        item {
                            if (bookmarks.isNotEmpty()) {
                                Text(
                                    "Collections",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(bookmarks, key = { it.url }) { b: Bookmark ->
                                        CollectionCard(b, onOpen = { onOpenUrl(b.url) }) {
                                            store.deleteBookmark(b.url)
                                            bookmarks = store.listBookmarks()
                                        }
                                    }
                                }
                            }
                        }
                        itemsIndexed(state.items) { idx, r: SearchResult ->
                            ResultCard(idx + 1, r) { onOpenUrl(r.url) }
                        }
                    }
                }
            }
        }
    }
}

/** Chrome-style rank card: big index, favicon tile, title/url/snippet. */
@Composable
private fun ResultCard(rank: Int, r: SearchResult, onOpen: () -> Unit) {
    val tile = faviconTile(r.url)
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                rank.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(30.dp),
            )
            // letter-tile favicon
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(tile.color)),
                contentAlignment = Alignment.Center,
            ) {
                Text(tile.letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    r.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (r.snippet.isNotBlank()) {
                    Text(
                        r.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(b: Bookmark, onOpen: () -> Unit, onDelete: () -> Unit) {
    val tile = faviconTile(b.url)
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(tile.color)),
                contentAlignment = Alignment.Center,
            ) { Text(tile.letter, color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 130.dp)) {
                Text(
                    b.title.ifBlank { b.url },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, "remove", Modifier.size(14.dp))
            }
        }
    }
}
