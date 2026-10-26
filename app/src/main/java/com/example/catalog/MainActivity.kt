package com.example.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.catalog.data.AppGraph
import com.example.catalog.ui.CatalogScreen
import com.example.catalog.ui.CatalogViewModel
import com.example.catalog.ui.DetailsScreen
import com.example.catalog.ui.DetailsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.attach(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    var openedId by rememberSaveable { mutableStateOf<Int?>(null) }
                    val catalog: CatalogViewModel = viewModel()
                    val details: DetailsViewModel = viewModel()
                    val id = openedId
                    if (id == null) {
                        CatalogScreen(catalog, onOpen = { openedId = it })
                    } else {
                        val back = {
                            details.close()
                            openedId = null
                        }
                        BackHandler(onBack = back)
                        LaunchedEffect(id) { details.open(id) }
                        DetailsScreen(details, onBack = back)
                    }
                }
            }
        }
    }
}
