package com.bqsqwad.share.ui

import android.Manifest
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
import com.bqsqwad.share.design.QR_SIZE
import com.bqsqwad.share.design.ScanFrame
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun QrScanner(onCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var error by remember { mutableStateOf<String?>(null) }
    var completed by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner: BarcodeScanner = remember { BarcodeScanning.getClient() }

    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera permission is required to scan a BQ Sqwad Share QR code.")
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        }
        return
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        val image = proxy.image
                        if (image == null || completed) {
                            proxy.close()
                        } else {
                            val input = InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)
                            scanner.process(input)
                                .addOnSuccessListener { barcodes ->
                                    val raw = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                                    val uri = runCatching { Uri.parse(raw) }.getOrNull()
                                    val code = uri?.getQueryParameter("code")
                                    val expires = uri?.getQueryParameter("expires")?.toLongOrNull() ?: 0L
                                    if (uri?.scheme == "bqsqwad" && code?.length == 6 && code.all(Char::isDigit)) {
                                        if (expires > System.currentTimeMillis()) {
                                            completed = true
                                            ContextCompat.getMainExecutor(ctx).execute { onCodeScanned(code) }
                                        } else {
                                            error = "This receive QR code has expired."
                                        }
                                    } else {
                                        error = "This is not a BQ Sqwad Share receive code."
                                    }
                                }
                                .addOnFailureListener { error = it.localizedMessage ?: "Unable to read QR code." }
                                .addOnCompleteListener { proxy.close() }
                        }
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }.onFailure { error = it.localizedMessage ?: "Unable to start camera." }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )
        ScanFrame(Modifier.size(QR_SIZE))
        Column(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Place the receive QR code inside the square", color = MaterialTheme.colorScheme.onSurface)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
