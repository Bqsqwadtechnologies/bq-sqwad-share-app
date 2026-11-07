package com.bqsqwad.share.vault

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.crypto.Cipher

@Composable
fun PrivateVaultScreen(activity: FragmentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val crypto = remember { VaultCryptoStore() }
    val vaultDirectory = remember(context) { File(context.filesDir, "private-vault") }
    var files by remember { mutableStateOf(emptyList<File>()) }
    var message by remember { mutableStateOf("Files are encrypted on this device and require strong biometric authentication.") }
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<File?>(null) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        files = vaultDirectory.listFiles()
            ?.filter { it.isFile && it.extension == "bqv" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destination: Uri? ->
        val prepared = pendingRestore
        pendingRestore = null
        if (prepared == null) return@rememberLauncherForActivityResult
        if (destination == null) {
            prepared.delete()
            message = "Restore cancelled."
        } else {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                            FileInputStream(prepared).use { input ->
                                val data = DataInputStream(BufferedInputStream(input))
                                data.readUTF() // Original name is stored inside the encrypted payload.
                                data.copyTo(output)
                            }
                        } ?: error("Unable to open the selected restore destination.")
                    }
                }
                prepared.delete()
                busy = false
                message = result.fold({ "File restored successfully." }, { it.message ?: "Restore failed." })
            }
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source: Uri? ->
        if (source == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                runCatching {
                    val name = displayName(context, source)
                    val payload = File(context.cacheDir, "vault-payload-${UUID.randomUUID()}.tmp")
                    try {
                        DataOutputStream(BufferedOutputStream(FileOutputStream(payload))).use { output ->
                            output.writeUTF(name.take(1024))
                            context.contentResolver.openInputStream(source)?.use { it.copyTo(output) }
                                ?: error("Unable to read the selected file.")
                        }
                        payload to name
                    } catch (failure: Throwable) {
                        payload.delete()
                        throw failure
                    }
                }
            }
            if (prepared.isFailure) {
                busy = false
                message = prepared.exceptionOrNull()?.message ?: "Could not prepare the selected file."
                return@launch
            }
            val (payload, _) = prepared.getOrThrow()
            val cipher = withContext(Dispatchers.IO) { runCatching { crypto.prepareEncryptCipher() } }
            if (cipher.isFailure) {
                payload.delete()
                busy = false
                message = cipher.exceptionOrNull()?.message ?: "Biometric vault is unavailable."
                return@launch
            }
            VaultBiometricGate.authenticate(
                activity,
                cipher.getOrThrow(),
                onAuthenticated = { authorized: Cipher ->
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                vaultDirectory.mkdirs()
                                crypto.encryptFile(authorized, payload, File(vaultDirectory, "${UUID.randomUUID()}.bqv"))
                            }
                        }
                        payload.delete()
                        busy = false
                        refresh()
                        message = result.fold({ "File encrypted and added to Private Vault." }, { it.message ?: "Encryption failed." })
                    }
                },
                onError = { error ->
                    payload.delete()
                    busy = false
                    message = error.toString()
                },
            )
        }
    }

    LaunchedEffect(vaultDirectory) { refresh() }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Private Vault", style = MaterialTheme.typography.headlineSmall)
        Text("Your vault is stored locally. Strong biometrics are required for every import, restore, and delete operation.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (!VaultBiometricGate.canAuthenticate(activity)) {
                    Text("Strong biometric authentication is not available. Set up a supported fingerprint or face unlock in Android settings to use the vault.")
                }
                Button(
                    onClick = { importPicker.launch(arrayOf("*/*")) },
                    enabled = !busy && VaultBiometricGate.canAuthenticate(activity),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Encrypt file into vault") }
            }
        }
        Text("Encrypted files · ${files.size}", style = MaterialTheme.typography.titleMedium)
        if (files.isEmpty()) {
            Text("Your Private Vault is empty.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.name }) { file ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Encrypted item", style = MaterialTheme.typography.titleSmall)
                                Text("${formatBytes(file.length())} · ${file.name.take(8)}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                enabled = !busy && VaultBiometricGate.canAuthenticate(activity),
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        val cipher = withContext(Dispatchers.IO) {
                                            runCatching { crypto.prepareDecryptCipher(file) }
                                        }
                                        if (cipher.isFailure) {
                                            busy = false
                                            message = cipher.exceptionOrNull()?.message ?: "Unable to prepare restore."
                                        } else {
                                            VaultBiometricGate.authenticate(
                                                activity,
                                                cipher.getOrThrow(),
                                                onAuthenticated = { authorized ->
                                                    scope.launch {
                                                        val restored = withContext(Dispatchers.IO) {
                                                            runCatching {
                                                                val temp = File(context.cacheDir, "vault-restore-${UUID.randomUUID()}.tmp")
                                                                crypto.decryptFile(authorized, file, temp)
                                                                val name = DataInputStream(BufferedInputStream(FileInputStream(temp))).use { it.readUTF() }
                                                                temp to name
                                                            }
                                                        }
                                                        busy = false
                                                        restored.fold(
                                                            { (temp, name) ->
                                                                pendingRestore = temp
                                                                restorePicker.launch(name)
                                                            },
                                                            { message = it.message ?: "Restore failed." },
                                                        )
                                                    }
                                                },
                                                onError = { error -> busy = false; message = error.toString() },
                                            )
                                        }
                                    }
                                },
                            ) { Text("Restore") }
                            TextButton(
                                enabled = !busy && VaultBiometricGate.canAuthenticate(activity),
                                onClick = { pendingDelete = file },
                            ) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete encrypted item?") },
            text = { Text("This permanently removes the selected encrypted vault file. Biometric authentication is required.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = pendingDelete
                    pendingDelete = null
                    if (target != null) {
                        busy = true
                        scope.launch {
                            val cipher = withContext(Dispatchers.IO) {
                                runCatching { crypto.prepareDecryptCipher(target) }
                            }
                            if (cipher.isFailure) {
                                busy = false
                                message = cipher.exceptionOrNull()?.message ?: "Unable to authorize deletion."
                            } else {
                                VaultBiometricGate.authenticate(
                                    activity,
                                    cipher.getOrThrow(),
                                    onAuthenticated = {
                                        scope.launch {
                                            val deleted = withContext(Dispatchers.IO) { target.delete() }
                                            busy = false
                                            refresh()
                                            message = if (deleted) "Encrypted item permanently deleted." else "Could not delete encrypted item."
                                        }
                                    },
                                    onError = { error -> busy = false; message = error.toString() },
                                )
                            }
                        }
                    }
                }) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "restored-file"
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024L * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
