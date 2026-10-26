package com.example.catalog.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DetailsScreen(viewModel: DetailsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().testTag(Tags.DETAILS)) {
        TextButton(onClick = onBack, modifier = Modifier.testTag(Tags.BACK)) { Text("Back") }
        when (val s = state) {
            DetailsState.Loading -> Centered { CircularProgressIndicator(Modifier.testTag(Tags.LOADING)) }
            is DetailsState.Error -> ErrorBlock(s.message, viewModel::retry)
            is DetailsState.Content -> Column(Modifier.padding(16.dp)) {
                Text(s.item.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag(Tags.DETAILS_TITLE))
                Text(formatPrice(s.item.price), modifier = Modifier.testTag(Tags.DETAILS_PRICE))
                Text(s.item.category)
                Text(
                    if (s.item.inStock) "In stock" else "Out of stock",
                    modifier = Modifier.testTag(Tags.DETAILS_STOCK),
                )
                Text(s.item.description, modifier = Modifier.padding(top = 12.dp).testTag(Tags.DETAILS_DESCRIPTION))
            }
        }
    }
}
