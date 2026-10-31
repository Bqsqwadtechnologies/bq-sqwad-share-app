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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingRequest(val endpointId: String, val name: String, val info: String)
data class PayloadEvent(val endpointId: String, val payload: Payload)
data class ProgressEvent(val endpointId: String, val payloadId: Long, val bytes: Long, val total: Long, val status: Int)

@Singleton
class NearbyTransport @Inject constructor(@ApplicationContext context: Context) {
    private val client = Nearby.getConnectionsClient(context)
    private val serviceId = "com.bqsqwad.share"
    private val strategy = Strategy.P2P_CLUSTER

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers

    private val _incoming = MutableStateFlow<IncomingRequest?>(null)
    val incoming: StateFlow<IncomingRequest?> = _incoming

    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected

    private val _payloads = MutableStateFlow<PayloadEvent?>(null)
    val payloads: StateFlow<PayloadEvent?> = _payloads

    private val _progress = MutableStateFlow<ProgressEvent?>(null)
    val progress: StateFlow<ProgressEvent?> = _progress

    var lastError: String? = null
        private set

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
            _incoming.value = IncomingRequest(endpointId, info.endpointName, info.authenticationDigits)
        }
        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                _connected.value = _connected.value + endpointId
            } else {
                lastError = resolution.status.statusMessage ?: "Connection declined"
            }
            _incoming.value = null
        }
        override fun onDisconnected(endpointId: String) {
            _connected.value = _connected.value - endpointId
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            _payloads.value = PayloadEvent(endpointId, payload)
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            _progress.value = ProgressEvent(endpointId, update.payloadId, update.bytesTransferred, update.totalBytes, update.status)
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
        client.requestConnection(name, endpointId, connectionCallback)
            .addOnFailureListener { lastError = it.message }
    }

    fun accept(endpointId: String) {
        client.acceptConnection(endpointId, payloadCallback)
        _incoming.value = null
    }

    fun reject(endpointId: String) {
        client.rejectConnection(endpointId)
        _incoming.value = null
    }

    fun disconnect(endpointId: String) {
        client.disconnectFromEndpoint(endpointId)
        _connected.value = _connected.value - endpointId
    }

    fun sendBytes(endpointId: String, bytes: ByteArray) {
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    fun sendFile(endpointId: String, file: File) {
        val payload = Payload.fromFile(file)
        client.sendPayload(endpointId, payload)
    }

    fun cancelPayload(id: Long) { client.cancelPayload(id) }
}
