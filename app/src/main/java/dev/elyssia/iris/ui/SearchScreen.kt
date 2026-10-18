package dev.elyssia.iris.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.elyssia.iris.search.Engine
import dev.elyssia.iris.search.SearchResult
import dev.elyssia.iris.search.SearchUiState

private fun engineLabel(engine: Engine) = when (engine) {
    Engine.SEARXNG -> "SearXNG"
    Engine.DUCKDUCKGO -> "DuckDuckGo"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQuerySubmit: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Search privately…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onQuerySubmit(text); text = "" },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Search") }
        Spacer(Modifier.height(8.dp))
        when (val state = uiState) {
            is SearchUiState.Idle -> {}
            is SearchUiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is SearchUiState.Error -> Column {
                Text("search unavailable — " + state.message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onQuerySubmit(state.query) }) { Text("Retry") }
            }
            is SearchUiState.Results -> {
                Text("via " + engineLabel(state.engine), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                LazyColumn {
                    items(state.items, key = { it.url }) { r: SearchResult ->
                        ElevatedCard(
                            onClick = { onOpenUrl(r.url) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(r.title, style = MaterialTheme.typography.titleMedium)
                                Text(r.url, style = MaterialTheme.typography.bodySmall)
                                if (r.snippet.isNotBlank()) {
                                    Text(r.snippet, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
