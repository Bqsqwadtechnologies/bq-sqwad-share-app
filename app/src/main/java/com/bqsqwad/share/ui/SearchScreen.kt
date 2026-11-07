package com.bqsqwad.share.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bqsqwad.share.ShareViewModel

@Composable
fun SearchScreen(nav: NavHostController, vm: ShareViewModel) {
    val context = LocalContext.current
    val history by vm.history.collectAsState()
    val apps = remember { vm.installedApps() }
    var query by remember { mutableStateOf("") }
    val term = query.trim()
    val matchingApps = apps.filter { term.isNotEmpty() && (it.first.contains(term, true) || it.second.contains(term, true)) }
    val matchingTransfers = history.filter { term.isNotEmpty() && it.status != "DELETED" && it.displayName.contains(term, true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Search", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search apps and transfers") },
        )
        if (term.isBlank()) {
            Text("Search your installed apps and transfer history.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (matchingApps.isNotEmpty()) {
                    item { Text("Apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(matchingApps, key = { it.second }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                context.packageManager.getLaunchIntentForPackage(app.second)?.let { launch ->
                                    runCatching { context.startActivity(launch) }
                                }
                            }.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(app.first, modifier = Modifier.weight(1f))
                            Text("Open", color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider()
                    }
                }
                if (matchingTransfers.isNotEmpty()) {
                    item { Text("Transfers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(matchingTransfers, key = { it.id }) { transfer ->
                        Column(
                            Modifier.fillMaxWidth().clickable { nav.navigate("history") }.padding(vertical = 10.dp, horizontal = 8.dp),
                        ) {
                            Text(transfer.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text("${transfer.direction} · ${transfer.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        HorizontalDivider()
                    }
                }
                if (matchingApps.isEmpty() && matchingTransfers.isEmpty()) {
                    item { Text("No matching apps or transfers.", modifier = Modifier.padding(12.dp)) }
                }
            }
        }
    }
}
