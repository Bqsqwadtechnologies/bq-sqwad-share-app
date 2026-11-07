package com.bqsqwad.share.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bqsqwad.share.common.SessionCrypto
import com.bqsqwad.share.design.QR_SIZE
import com.bqsqwad.share.design.ScanFrame
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScanner(onCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) error = "Camera permission was not granted."
    }

    LaunchedEffect(granted) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Camera permission is required to scan a BQ Sqwad Share QR code.")
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        return
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner: BarcodeScanner = remember { BarcodeScanning.getClient() }
    val active = remember { AtomicBoolean(true) }
    val completed = remember { AtomicBoolean(false) }
    val processing = remember { AtomicBoolean(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(lifecycleOwner, scanner, executor) {
        active.set(true)
        onDispose {
            active.set(false)
            cameraProvider?.unbindAll()
            cameraProvider = null
            executor.shutdownNow()
            scanner.close()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    val providerFuture = ProcessCameraProvider.getInstance(viewContext.applicationContext)
                    providerFuture.addListener({
                        if (!active.get()) return@addListener
                        runCatching {
                            val provider = providerFuture.get()
                            cameraProvider = provider
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { proxy ->
                                val mediaImage = proxy.image
                                if (
                                    mediaImage == null || !active.get() || completed.get() ||
                                    !processing.compareAndSet(false, true)
                                ) {
                                    proxy.close()
                                } else {
                                    try {
                                        val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                                        scanner.process(input)
                                            .addOnSuccessListener(ContextCompat.getMainExecutor(viewContext)) { barcodes ->
                                                if (!active.get() || completed.get()) return@addOnSuccessListener
                                                val raw = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                                                val uri = runCatching { Uri.parse(raw) }.getOrNull()
                                                val code = uri?.getQueryParameter("code")
                                                val expires = uri?.getQueryParameter("expires")?.toLongOrNull() ?: 0L
                                                val encodedKey = uri?.getQueryParameter("key")
                                                val fingerprint = uri?.getQueryParameter("v")
                                                val keyValid = if (!encodedKey.isNullOrBlank() && !fingerprint.isNullOrBlank()) {
                                                    runCatching {
                                                        val key = SessionCrypto.keyFromString(encodedKey)
                                                        key.encoded.size == 32 && SessionCrypto.fingerprint(key) == fingerprint
                                                    }.getOrDefault(false)
                                                } else false
                                                if (
                                                    uri?.scheme == "bqsqwad" &&
                                                    code != null && code.length == 6 && code.all(Char::isDigit) &&
                                                    expires > System.currentTimeMillis() && keyValid
                                                ) {
                                                    if (completed.compareAndSet(false, true)) onCodeScanned(raw)
                                                } else {
                                                    error = if (expires > 0L && expires <= System.currentTimeMillis()) {
                                                        "This receive QR code has expired."
                                                    } else {
                                                        "This is not a valid BQ Sqwad Share receive QR code."
                                                    }
                                                }
                                            }
                                            .addOnFailureListener(ContextCompat.getMainExecutor(viewContext)) {
                                                if (active.get()) error = it.localizedMessage ?: "Unable to read QR code."
                                            }
                                            .addOnCompleteListener {
                                                processing.set(false)
                                                proxy.close()
                                            }
                                    } catch (failure: Exception) {
                                        processing.set(false)
                                        proxy.close()
                                        ContextCompat.getMainExecutor(viewContext).execute {
                                            if (active.get()) error = failure.localizedMessage ?: "Unable to process camera frame."
                                        }
                                    }
                                }
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }.onFailure {
                            if (active.get()) error = it.localizedMessage ?: "Unable to start the camera."
                        }
                    }, ContextCompat.getMainExecutor(viewContext))
                }
            },
        )
        ScanFrame(Modifier.size(QR_SIZE))
        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Place the receive QR code inside the square", color = MaterialTheme.colorScheme.onSurface)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
