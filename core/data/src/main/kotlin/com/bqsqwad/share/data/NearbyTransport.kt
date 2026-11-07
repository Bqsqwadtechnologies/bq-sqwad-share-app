package com.bqsqwad.share.data

import android.content.Context
import com.bqsqwad.share.domain.Peer
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingRequest(val endpointId: String, val name: String, val info: String)
data class PayloadEvent(val endpointId: String, val payload: Payload)
data class ProgressEvent(val endpointId: String, val payloadId: Long, val bytes: Long, val total: Long, val status: Int)

@Singleton
class NearbyTransport @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsStore: SettingsStore,
) {
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = Nearby.getConnectionsClient(context)
    private val serviceId = "com.bqsqwad.share"
    private val strategy = Strategy.P2P_CLUSTER

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers

    private val _incoming = MutableStateFlow<IncomingRequest?>(null)
    val incoming: StateFlow<IncomingRequest?> = _incoming

    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected

    // Track pending Nearby handshakes so the UI can show live connection progress.
    private val _connecting = MutableStateFlow<Set<String>>(emptySet())
    val connecting: StateFlow<Set<String>> = _connecting

    private fun markConnecting(endpointId: String) {
        _connecting.value = _connecting.value + endpointId
    }

    private fun clearConnecting(endpointId: String) {
        _connecting.value = _connecting.value - endpointId
    }

    private val _payloads = MutableSharedFlow<PayloadEvent>(extraBufferCapacity = 64)
    val payloads: SharedFlow<PayloadEvent> = _payloads

    private val _progress = MutableSharedFlow<ProgressEvent>(extraBufferCapacity = 128)
    val progress: SharedFlow<ProgressEvent> = _progress

    var lastError: String? = null
        private set
    @Volatile private var expectedReceiveCode: String = ""

    fun setExpectedReceiveCode(code: String) { expectedReceiveCode = code.filter(Char::isDigit).take(6) }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _peers.value = (_peers.value.filterNot { it.endpointId == endpointId } + Peer(endpointId, info.endpointName))
        }
        override fun onEndpointLost(endpointId: String) {
            _peers.value = _peers.value.filterNot { it.endpointId == endpointId }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            markConnecting(endpointId)
            callbackScope.launch {
                val blocked = runCatching { settingsStore.settings.first().blocked }.getOrDefault(emptySet())
                if (info.endpointName in blocked || endpointId in blocked) {
                    client.rejectConnection(endpointId)
                    clearConnecting(endpointId)
                    _incoming.value = null
                    return@launch
                }
                val expected = expectedReceiveCode
                if (expected.length == 6) {
                    if (info.endpointName == "BQ Sqwad Share code $expected") {
                        client.acceptConnection(endpointId, payloadCallback)
                    } else {
                        client.rejectConnection(endpointId)
                        clearConnecting(endpointId)
                    }
                    _incoming.value = null
                } else {
                    _incoming.value = IncomingRequest(endpointId, info.endpointName, info.authenticationDigits)
                }
            }
        }
        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            clearConnecting(endpointId)
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                _connected.value = _connected.value + endpointId
            } else {
                lastError = resolution.status.statusMessage ?: "Connection declined"
            }
            _incoming.value = null
        }
        override fun onDisconnected(endpointId: String) {
            clearConnecting(endpointId)
            _connected.value = _connected.value - endpointId
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            _payloads.tryEmit(PayloadEvent(endpointId, payload))
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            _progress.tryEmit(ProgressEvent(endpointId, update.payloadId, update.bytesTransferred, update.totalBytes, update.status))
        }
    }

    fun startDiscovery() {
        client.startDiscovery(serviceId, discoveryCallback, DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { lastError = it.message }
    }

    fun stopDiscovery() { client.stopDiscovery() }

    fun startAdvertising(name: String) {
        client.startAdvertising(name, serviceId, connectionCallback, AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { lastError = it.message }
    }

    fun stopAdvertising() { client.stopAdvertising() }

    fun requestConnection(endpointId: String, name: String) {
        markConnecting(endpointId)
        client.requestConnection(name, endpointId, connectionCallback)
            .addOnFailureListener {
                clearConnecting(endpointId)
                lastError = it.message
            }
    }

    fun accept(endpointId: String) {
        client.acceptConnection(endpointId, payloadCallback)
        _incoming.value = null
    }

    fun reject(endpointId: String) {
        clearConnecting(endpointId)
        client.rejectConnection(endpointId)
        _incoming.value = null
    }

    fun disconnect(endpointId: String) {
        clearConnecting(endpointId)
        client.disconnectFromEndpoint(endpointId)
        _connected.value = _connected.value - endpointId
    }

    fun sendBytes(endpointId: String, bytes: ByteArray) {
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    fun sendFile(endpointId: String, file: File): Long {
        val payload = Payload.fromFile(file)
        client.sendPayload(endpointId, payload)
        return payload.id
    }

    fun cancelPayload(id: Long) { client.cancelPayload(id) }
}
