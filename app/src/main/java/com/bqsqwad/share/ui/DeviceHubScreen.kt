package com.bqsqwad.share.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bqsqwad.share.ShareViewModel

@Composable
fun DeviceHubScreen(nav: NavHostController, vm: ShareViewModel) {
    val peers by vm.nearby.peers.collectAsState()
    val connected by vm.nearby.connected.collectAsState()
    val activePeers = peers.filter { it.endpointId in connected }
    val nearbyPeers = peers.filterNot { it.endpointId in connected }
    val savedDevices by vm.trustedDeviceHistory.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Device Hub", style = MaterialTheme.typography.headlineSmall)
        Text("Manage devices connected to this phone. Pairing still requires the six-digit receive code.")
        Text("Connected devices · ${connected.size}", style = MaterialTheme.typography.titleMedium)
        if (activePeers.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("No active connections")
                    Text("Connect from Send after the receiving device opens Receive and shares its code.")
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activePeers.forEach { peer ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(peer.name, style = MaterialTheme.typography.titleSmall)
                                Text("Connected · nearby session", style = MaterialTheme.typography.bodySmall)
                                if (peer.trusted) {
                                    Text("Trusted device · saved", style = MaterialTheme.typography.labelSmall)
                                    OutlinedButton(onClick = { peer.stableIdentity?.let(vm::untrustDevice) }) { Text("Remove trust") }
                                } else if (peer.stableIdentity != null) {
                                    OutlinedButton(onClick = { vm.trustDevice(peer.endpointId) }) { Text("Trust this device") }
                                } else {
                                    Text("Verifying device identity…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            OutlinedButton(onClick = { vm.nearby.disconnect(peer.endpointId) }) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }
        }
        if (nearbyPeers.isNotEmpty()) {
            Text("Discovered nearby · ${nearbyPeers.size}", style = MaterialTheme.typography.titleMedium)
            nearbyPeers.forEach { peer ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(peer.name, style = MaterialTheme.typography.titleSmall)
                            Text("Not connected", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { nav.navigate("send") }) { Text("Pair in Send") }
                    }
                }
            }
        }
        if (!savedDevices?.trustedDevices.isNullOrEmpty()) {
            Text("Saved trusted devices", style = MaterialTheme.typography.titleMedium)
            savedDevices?.trustedDevices?.forEach { (identity, name) ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text("Saved identity · " + identity.take(8) + "…", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { vm.untrustDevice(identity) }) { Text("Remove") }
                    }
                }
            }
        }
        Text("Trusted identities are exchanged over Nearby and saved on this device. Trust is never granted automatically; approve each device here.", style = MaterialTheme.typography.bodySmall)
    }
}
