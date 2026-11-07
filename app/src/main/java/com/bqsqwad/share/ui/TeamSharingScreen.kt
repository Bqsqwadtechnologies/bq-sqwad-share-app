package com.bqsqwad.share.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import android.widget.Toast
import com.bqsqwad.share.ShareViewModel

@Composable
fun TeamSharingScreen(vm: ShareViewModel) {
    val context = LocalContext.current
    val connected by vm.nearby.connected.collectAsState()
    val discovered by vm.nearby.peers.collectAsState()
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var picked by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val activeSelection = selected.intersect(connected)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked = uris
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Team Sharing", style = MaterialTheme.typography.headlineSmall)
        Text("Select recipients and files. Only the checked, currently connected devices receive this batch.")
        Text("Connected devices · ${connected.size}", style = MaterialTheme.typography.titleMedium)
        if (connected.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text("Connect to nearby devices first, then return here to choose recipients.", modifier = Modifier.padding(16.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected = connected }) { Text("Select all") }
                OutlinedButton(onClick = { selected = emptySet() }) { Text("Clear selection") }
            }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(connected.toList(), key = { it }) { endpointId ->
                    val peerName = discovered.firstOrNull { it.endpointId == endpointId }?.name ?: "Nearby device"
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = endpointId in activeSelection,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + endpointId else selected - endpointId
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(peerName, style = MaterialTheme.typography.titleSmall)
                                Text("Connected for this session", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Text("${activeSelection.size} recipient(s) selected")
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Choose files")
            }
            picked.forEach { uri -> Text(uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file", maxLines = 1) }
            Button(
                onClick = {
                    if (activeSelection.isEmpty() || picked.isEmpty()) {
                        Toast.makeText(context, "Choose at least one recipient and one file.", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.queueFiles(picked)
                        vm.sendQueuedFilesToRecipients(activeSelection.toList())
                        picked = emptyList()
                    }
                },
                enabled = activeSelection.isNotEmpty() && picked.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send files to selected devices") }
        }
        Text(
            "Recipients are selected for this transfer only. Saved teams, persistent device permissions, and background group delivery are not enabled.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
