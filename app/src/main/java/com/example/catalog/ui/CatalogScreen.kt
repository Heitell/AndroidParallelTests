package com.example.catalog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.catalog.data.Item

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(viewModel: CatalogViewModel, onOpen: (Int) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f).testTag(Tags.SEARCH_FIELD),
                singleLine = true,
                placeholder = { Text("Search") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            )
            Button(onClick = viewModel::search, modifier = Modifier.testTag(Tags.SEARCH_BUTTON)) {
                Text("Find")
            }
            if (state.query.isNotEmpty()) {
                TextButton(onClick = viewModel::clearSearch, modifier = Modifier.testTag(Tags.CLEAR_BUTTON)) {
                    Text("Clear")
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().testTag(Tags.REFRESH_BOX),
        ) {
            when (val list = state.list) {
                ListState.Loading -> Centered {
                    CircularProgressIndicator(Modifier.testTag(Tags.LOADING))
                }
                ListState.Empty -> Centered {
                    Text("Nothing found", Modifier.testTag(Tags.EMPTY))
                }
                is ListState.Error -> ErrorBlock(list.message, viewModel::retry)
                is ListState.Content -> ItemList(list.items, onOpen)
            }
        }
    }
}

@Composable
private fun ItemList(items: List<Item>, onOpen: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag(Tags.LIST)) {
        items(items, key = { it.id }) { item ->
            Column(
                Modifier.fillMaxWidth()
                    .clickable { onOpen(item.id) }
                    .padding(16.dp)
                    .testTag(Tags.item(item.id)),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.category, style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatPrice(item.price),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(Tags.itemPrice(item.id)),
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Centered {
        Column(Modifier.testTag(Tags.ERROR), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, Modifier.testTag(Tags.ERROR_MESSAGE))
            Button(onClick = onRetry, modifier = Modifier.testTag(Tags.RETRY)) { Text("Retry") }
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
