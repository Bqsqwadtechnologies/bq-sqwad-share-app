package com.bqsqwad.share.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.bqsqwad.share.R
import com.bqsqwad.share.ShareViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color
import java.util.concurrent.atomic.AtomicBoolean

private fun nearbyPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.NEARBY_WIFI_DEVICES,
    )
    Build.VERSION.SDK_INT >= 31 -> arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
    )
    else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun qrImage(value: String): Bitmap {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 720, 720)
    return Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until 720) for (y in 0 until 720) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
}

@Composable
fun SendQrScreen(nav: NavHostController, vm: ShareViewModel) {
    val context = LocalContext.current
    val incoming by vm.nearby.incoming.collectAsState()
    var permissionReady by remember { mutableStateOf(nearbyPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionReady = nearbyPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (permissionReady) vm.nearby.startAdvertising("BQ Sqwad Share")
        else permissionMessage = context.getString(R.string.permission_denied)
    }
    LaunchedEffect(Unit) {
        if (permissionReady) vm.nearby.startAdvertising("BQ Sqwad Share")
        else permissionLauncher.launch(nearbyPermissions())
    }
    DisposableEffect(Unit) {
        onDispose { vm.nearby.stopAdvertising() }
    }
    val payload = "bqsqwad://share?code=${vm.sessionCode}&key=${vm.keyText}&v=${vm.verification}"
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Send files", style = MaterialTheme.typography.headlineMedium)
        Text("Let the other phone scan this code to find your device.", modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(22.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (permissionReady) {
                    Image(
                        bitmap = remember(payload) { qrImage(payload) }.asImageBitmap(),
                        contentDescription = "QR code for receiving a connection",
                        modifier = Modifier.size(280.dp).padding(8.dp),
                    )
                    Text("Scan with BQ Sqwad Share", style = MaterialTheme.typography.titleMedium)
                    Text("Code: ${vm.sessionCode}", style = MaterialTheme.typography.titleLarge)
                    Text("Verification: ${vm.verification}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(permissionMessage ?: "Nearby access is needed to make this phone discoverable.")
                    Button(onClick = { permissionLauncher.launch(nearbyPermissions()) }) { Text("Allow nearby access") }
                }
            }
        }
        incoming?.let { request ->
            Spacer(Modifier.height(18.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Connection request", style = MaterialTheme.typography.titleMedium)
                    Text("${request.name} · ${request.info}")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                        Button(onClick = {
                            vm.nearby.accept(request.endpointId)
                            nav.navigate("picker")
                        }) { Text("Accept") }
                        OutlinedButton(onClick = { vm.nearby.reject(request.endpointId) }) { Text("Decline") }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { nav.popBackStack() }) { Text("Back") }
    }
}

@Composable
fun ReceiveQrScreen(vm: ShareViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var nearbyGranted by remember {
        mutableStateOf(nearbyPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
    }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (granted && !nearbyGranted) nearbyPermissionLauncherRef?.invoke()
        else if (!granted) permissionMessage = context.getString(R.string.permission_denied)
    }
    val nearbyPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        nearbyGranted = nearbyPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!nearbyGranted) permissionMessage = context.getString(R.string.permission_denied)
    }
    nearbyPermissionLauncherRef = { nearbyPermissionLauncher.launch(nearbyPermissions()) }
    LaunchedEffect(Unit) {
        if (!cameraGranted) cameraPermission.launch(Manifest.permission.CAMERA)
        else if (!nearbyGranted) nearbyPermissionLauncher.launch(nearbyPermissions())
    }
    val peers by vm.nearby.peers.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Receive files", style = MaterialTheme.typography.headlineMedium)
        Text("Point your camera at the QR code shown on the sender's phone.")
        Spacer(Modifier.height(14.dp))
        if (!cameraGranted) {
            Text(permissionMessage ?: "Camera access is used only to scan the connection QR code.")
            Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        } else if (scanResult == null) {
            QrCameraPreview(
                modifier = Modifier.fillMaxWidth().weight(1f),
                lifecycleOwner = lifecycleOwner,
                onQr = { value ->
                    if (value.startsWith("bqsqwad://share")) {
                        scanResult = value
                        if (!nearbyGranted) nearbyPermissionLauncher.launch(nearbyPermissions())
                        else vm.nearby.startDiscovery()
                    } else {
                        Toast.makeText(context, "This QR code is not a BQ Sqwad Share code.", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        } else {
            Text("QR code scanned", style = MaterialTheme.typography.titleLarge)
            Text("Searching for the sender nearby…")
            Text("Session: ${runCatching { android.net.Uri.parse(scanResult).getQueryParameter("code") }.getOrNull() ?: "verified"}")
            if (nearbyGranted) vm.nearby.startDiscovery()
            peers.forEach { peer ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(peer.name, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            vm.nearby.requestConnection(peer.endpointId, "BQ Sqwad Share")
                            Toast.makeText(context, "Connection request sent. Accept it on the sending phone.", Toast.LENGTH_LONG).show()
                        }) { Text("Connect") }
                    }
                }
            }
            if (peers.isEmpty()) Text("Keep both phones close and make sure the sender is showing the QR code.")
            OutlinedButton(onClick = { scanResult = null }) { Text("Scan again") }
        }
        if (permissionMessage != null) Text(permissionMessage!!)
    }
}

private var nearbyPermissionLauncherRef: (() -> Unit)? = null

@Composable
private fun QrCameraPreview(
    modifier: Modifier,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onQr: (String) -> Unit,
) {
    val context = LocalContext.current
    val delivered = remember { AtomicBoolean(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
    }
    DisposableEffect(Unit) {
        onDispose { scanner.close() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                    val image = proxy.image
                    if (image == null || delivered.get()) {
                        proxy.close()
                    } else {
                        val input = InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)
                        scanner.process(input)
                            .addOnSuccessListener { codes ->
                                val value = codes.firstOrNull()?.rawValue
                                if (!value.isNullOrBlank() && delivered.compareAndSet(false, true)) onQr(value)
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Unable to start the camera preview.", Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
