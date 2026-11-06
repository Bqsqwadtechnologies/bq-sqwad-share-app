
package com.bqsqwad.share.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bqsqwad.share.BuildConfig
import com.bqsqwad.share.R
import com.bqsqwad.share.ShareViewModel
import com.bqsqwad.share.ads.AdManager
import com.bqsqwad.share.ads.BannerSlot
import com.bqsqwad.share.ads.NativeSlot
import com.bqsqwad.share.data.UserSettings
import com.bqsqwad.share.design.ShareTheme
import com.bqsqwad.share.design.QR_SIZE
import com.bqsqwad.share.design.ActionTile
import com.bqsqwad.share.design.GradientHero
import com.bqsqwad.share.design.SectionTitle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.delay

private const val TERMS = "https://bqsqwadtechnologies.github.io/bq-sqwad-share-app/terms.html"
private const val PRIVACY = "https://bqsqwadtechnologies.github.io/bq-sqwad-share-app/privacy.html"

@Composable
fun ShareAppRoot(activity: Activity, vm: ShareViewModel, settings: UserSettings?, ads: AdManager) {
    val dark = when (settings?.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    ShareTheme(dark = dark, accent = settings?.accent ?: "royal") {
        if (settings == null) {
            Column(Modifier.fillMaxSize().padding(24.dp)) { Text(stringResource(R.string.app_name)) }
        } else if (!settings.ageConfirmed) {
            AgeGate { vm.confirmAge() }
        } else {
            val nav = rememberNavController()
            val adFree = settings.adFreeUntil > System.currentTimeMillis()
            val backStackEntry by nav.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            BrandHeader()
                            Text("SHARE", style = MaterialTheme.typography.labelLarge)
                            listOf("Send" to "send", "Scan QR" to "scan", "Receive" to "receive", "Computer/iPhone" to "computer", "Phone Clone" to "clone").forEach { (label, route) ->
                                NavigationDrawerItem(label = { Text(label) }, selected = false, onClick = {
                                    scope.launch { drawerState.close() }; nav.navigate(route)
                                })
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("BROWSE", style = MaterialTheme.typography.labelLarge)
                            listOf("Files" to "files", "Apps" to "library/apps", "Photos" to "library/pictures", "Videos" to "library/videos", "Music" to "library/music", "Documents" to "files", "Received" to "library/received", "Sent" to "library/sent").forEach { (label, route) ->
                                NavigationDrawerItem(label = { Text(label) }, selected = currentRoute == route, onClick = {
                                    scope.launch { drawerState.close() }; nav.navigate(route)
                                })
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("TOOLS", style = MaterialTheme.typography.labelLarge)
                            listOf("Clean" to "cleaner", "Favorites" to "favorites", "Chat" to "chat").forEach { (label, route) ->
                                NavigationDrawerItem(label = { Text(label) }, selected = currentRoute == route, onClick = {
                                    scope.launch { drawerState.close() }; nav.navigate(route)
                                })
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("MORE", style = MaterialTheme.typography.labelLarge)
                            listOf("Settings" to "settings", "About and more apps" to "more").forEach { (label, route) ->
                                NavigationDrawerItem(label = { Text(label) }, selected = currentRoute == route, onClick = {
                                    scope.launch { drawerState.close() }; nav.navigate(route)
                                })
                            }
                        }
                    }
                },
            ) {
            Scaffold(topBar = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.brand_logo), contentDescription = "BQ Sqwad Share logo", modifier = Modifier.size(42.dp))
                        Column(Modifier.padding(start = 10.dp)) {
                            Text("BQ Sqwad Share", style = MaterialTheme.typography.titleMedium)
                            Text("BQ Sqwad Technologies", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { nav.navigate("more") }) { Text("About") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            Triple("Home", "home", Icons.Filled.Home),
                            Triple("Files", "files", Icons.Filled.FolderOpen),
                            Triple("Transfers", "history", Icons.Filled.History),
                            Triple("Clean", "cleaner", Icons.Filled.Apps),
                            Triple("Settings", "settings", Icons.Filled.Settings),
                        ).forEach { (label, route, icon) ->
                            FilterChip(selected = currentRoute == route, onClick = { nav.navigate(route) { launchSingleTop = true } }, label = { Text(label) }, leadingIcon = { androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) })
                        }
                    }
                }
            }, bottomBar = {
                BannerSlot(enabled = !adFree && ads.canRequestAds, modifier = Modifier.navigationBarsPadding())
            }) { adPadding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(adPadding)) {
                composable("home") { Home(nav, vm, ads, adFree) }
                composable("files") { FilesBrowser(nav, vm) }
                composable("send") { Send(nav, vm) }
                composable("scan") { QrScanner { code -> vm.setScannedReceiveCode(code); nav.navigate("send") { popUpTo("scan") { inclusive = true } } } }
                composable("receive") { Receive(vm) }
                composable("picker") { Picker(nav, vm, ads, adFree) }
                composable("history") { History(vm, ads, adFree) }
                composable("library/{section}") { entry -> FilesBrowser(nav, vm, initialTab = entry.arguments?.getString("section").orEmpty()) }
                composable("settings") { Settings(nav, vm, ads, activity) }
                composable("clone") { Gated(settings, ads, activity, vm, "clone") { Clone(vm) } }
                composable("computer") { Gated(settings, ads, activity, vm, "computer") { Computer(vm) } }
                composable("cleaner") { Cleaner(vm) }
                composable("favorites") { Favorites(vm) }
                composable("chat") { Chat(vm) }
                composable("more") { More() }
                composable("debug") { Debug(ads) }
            }
            }
            }
        }
    }
}

@Composable
private fun AgeGate(onYes: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        BrandHeader()
        Text(stringResource(R.string.age_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.age_body))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.perm_body))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onYes, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.age_confirm))
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(56.dp),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.company))
        }
    }
}

@Composable
private fun Home(nav: NavHostController, vm: ShareViewModel, ads: AdManager, adFree: Boolean) {
    val history by vm.history.collectAsState()
    val (free, total) = vm.storage()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BrandHeader()
            Card(Modifier.fillMaxWidth().height(220.dp).clickable { nav.navigate("files") }) {
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painter = painterResource(R.drawable.brand_logo), contentDescription = "BQ Sqwad Share", modifier = Modifier.size(88.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("BQ SQWAD SHARE", style = MaterialTheme.typography.headlineSmall)
                    Text("Tap to browse and share files", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { nav.navigate("send") }, modifier = Modifier.weight(1f).height(54.dp)) {
                    androidx.compose.material3.Icon(Icons.Filled.Send, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Send")
                }
                OutlinedButton(onClick = { nav.navigate("receive") }, modifier = Modifier.weight(1f).height(54.dp)) { Text("Receive") }
            }
            Text("Browse your phone", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(Triple("Apps", "apps", Icons.Filled.Apps), Triple("Photos", "photos", Icons.Filled.PhotoLibrary)).forEach { (label, section, icon) ->
                    Card(Modifier.weight(1f).clickable { nav.navigate("library/$section") }) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
                            Text(label)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(Triple("Videos", "videos", Icons.Filled.VideoLibrary), Triple("Music", "music", Icons.Filled.MusicNote)).forEach { (label, section, icon) ->
                    Card(Modifier.weight(1f).clickable { nav.navigate("library/$section") }) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
                            Text(label)
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Phone storage", style = MaterialTheme.typography.titleMedium)
                    Text("${free / 1_048_576} MB free of ${total / 1_048_576} MB")
                    if (free < 200L * 1_048_576) Text(stringResource(R.string.low_storage))
                }
            }
            Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleLarge)
        }
        if (history.isEmpty()) item { Text(stringResource(R.string.empty_recent)) }
        itemsIndexed(history.take(5), key = { _, item -> item.id }) { _, item ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(if (item.direction.equals("SEND", true)) Icons.Filled.Send else Icons.Filled.FolderOpen, contentDescription = null)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("${item.direction} · ${item.status}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Send(nav: NavHostController, vm: ShareViewModel) {
    val peers by vm.nearby.peers.collectAsState()
    val scannedCode by vm.scannedReceiveCode.collectAsState()
    var receiveCode by remember { mutableStateOf(scannedCode) }
    LaunchedEffect(scannedCode) { if (scannedCode.isNotBlank()) receiveCode = scannedCode }
    val matchingPeers = if (receiveCode.length == 6) peers.filter { it.name.contains(receiveCode) } else peers
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) vm.nearby.startDiscovery()
    }
    LaunchedEffect(Unit) {
        val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE, android.Manifest.permission.NEARBY_WIFI_DEVICES)
        } else if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        perms.launch(requiredPermissions)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.looking), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.privacy_note))
        androidx.compose.material3.OutlinedTextField(
            value = receiveCode,
            onValueChange = { receiveCode = it.filter(Char::isDigit).take(6) },
            label = { Text("Enter 6-digit receive code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (matchingPeers.isEmpty()) Text(if (receiveCode.length == 6) "No nearby device is advertising this code." else stringResource(R.string.not_found), modifier = Modifier.padding(vertical = 12.dp))
        matchingPeers.forEach { peer ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                val displayName = if (receiveCode.length == 6) "BQ Sqwad Share code $receiveCode" else "BQ Sqwad Share"
                vm.nearby.requestConnection(peer.endpointId, displayName)
                nav.navigate("picker")
            }) { Text(peer.name, modifier = Modifier.padding(16.dp)) }
        }
        Button(onClick = { nav.navigate("scan") }, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.scan_qr)) }
    }
}

@Composable
private fun Receive(vm: ShareViewModel) {
    val context = LocalContext.current
    val incoming by vm.nearby.incoming.collectAsState()
    val expiresAt by vm.receiveExpiresAt.collectAsState()
    var remainingSeconds by remember { mutableStateOf(0L) }
    val receivePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) vm.startReceiveSession()
        else Toast.makeText(context, "Nearby permissions are required to receive files.", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(Unit) {
        val required = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE, android.Manifest.permission.NEARBY_WIFI_DEVICES)
        } else if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        receivePermissions.launch(required)
    }
    DisposableEffect(Unit) {
        onDispose { vm.stopReceiveSession() }
    }
    LaunchedEffect(expiresAt) {
        while (expiresAt > 0L) {
            val remaining = ((expiresAt - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L)
            remainingSeconds = remaining
            if (remaining <= 0L) {
                vm.expireReceiveSessionIfNeeded()
                break
            }
            delay(1000L)
        }
    }
    val code = if (expiresAt > 0L && remainingSeconds > 0L) vm.sessionCode else ""
    val payload = if (code.isNotBlank()) "bqsqwad://share?code=$code&expires=$expiresAt&key=${vm.keyText}&v=${vm.verification}" else ""
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.show_qr), style = MaterialTheme.typography.titleLarge)
        if (code.isNotBlank()) {
            Image(
                bitmap = qrBitmap(payload).asImageBitmap(),
                contentDescription = stringResource(R.string.show_qr),
                modifier = Modifier.size(QR_SIZE),
            )
            Text(stringResource(R.string.qr_help))
            Text("${stringResource(R.string.manual_code)}: $code", style = MaterialTheme.typography.headlineSmall)
            Text("Code expires in %02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60))
        } else {
            Text(if (expiresAt == 0L) "Allow Nearby permissions to create a receive code." else "This receive code has expired. Generate a new one to receive files.")
        }
        Button(onClick = { vm.startReceiveSession() }) { Text(if (code.isBlank()) "Generate new code" else "Regenerate code") }
        Text("${stringResource(R.string.verification)}: ${vm.verification}")
        incoming?.let { req ->
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.accept_title))
            Text("${req.name} · ${req.info}")
            Row {
                Button(onClick = { vm.nearby.accept(req.endpointId) }) { Text(stringResource(R.string.accept)) }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { vm.nearby.reject(req.endpointId) }) { Text(stringResource(R.string.decline)) }
                TextButton(onClick = { vm.block(req.name); vm.nearby.reject(req.endpointId) }) { Text(stringResource(R.string.block)) }
            }
        }
    }
}

@Composable
private fun Picker(nav: NavHostController, vm: ShareViewModel, ads: AdManager, adFree: Boolean) {
    var tab by remember { mutableStateOf("files") }
    val ctx = LocalContext.current
    val picked = remember { mutableStateOf(listOf<Uri>()) }
    val queued by vm.pendingUris.collectAsState()
    val queuedLocal by vm.pendingLocalFiles.collectAsState()
    val autoSend by vm.autoSendPending.collectAsState()
    val connected by vm.nearby.connected.collectAsState()
    LaunchedEffect(connected, queued, queuedLocal, autoSend) {
        val endpointId = connected.firstOrNull()
        if (autoSend && endpointId != null && (queued.isNotEmpty() || queuedLocal.isNotEmpty())) {
            vm.sendQueuedFiles(endpointId)
            ads.onTaskCompleted(ctx as Activity, adFree)
            nav.popBackStack()
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked.value = uris
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("files" to R.string.files, "apps" to R.string.apps, "clip" to R.string.clipboard).forEach { (id, label) ->
                FilterChip(selected = tab == id, onClick = { tab = id }, label = { Text(stringResource(label)) })
            }
        }
        when (tab) {
            "apps" -> {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    itemsIndexed(vm.installedApps(), key = { _, it -> it.second }) { index, app ->
                        if (index > 0 && index % 8 == 0) NativeSlot(!adFree && ads.canRequestAds)
                        Text("${app.first}\n${app.second}", modifier = Modifier.padding(vertical = 8.dp).clickable {
                            vm.record(app.first + ".apk", "application/vnd.android.package-archive", 0, "SEND", "nearby", null)
                        })
                    }
                }
            }
            "clip" -> {
                var text by remember { mutableStateOf("") }
                androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.clipboard)) })
                Button(onClick = {
                    val id = vm.nearby.connected.value.firstOrNull()
                    if (id == null) Toast.makeText(ctx, ctx.getString(R.string.not_found), Toast.LENGTH_SHORT).show()
                    else vm.sendText(id, text)
                }, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.send)) }
            }
            else -> {
                Button(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.pick_files)) }
                Text(stringResource(R.string.photos) + " · " + stringResource(R.string.videos) + " · " + stringResource(R.string.music) + " · " + stringResource(R.string.documents))
                picked.value.forEach { Text(it.lastPathSegment ?: it.toString(), modifier = Modifier.padding(vertical = 4.dp)) }
                if (queued.isNotEmpty()) Text("${queued.size} item(s) selected from Files", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(onClick = {
            if (picked.value.isNotEmpty()) vm.queueFiles(picked.value)
            val endpointId = connected.firstOrNull()
            if (endpointId == null) {
                Toast.makeText(ctx, "Connect to a nearby device first, then send the selected files.", Toast.LENGTH_LONG).show()
            } else if (queued.isNotEmpty() || queuedLocal.isNotEmpty() || picked.value.isNotEmpty()) {
                vm.sendQueuedFiles(endpointId)
                ads.onTaskCompleted(ctx as Activity, adFree)
                nav.popBackStack()
            } else {
                Toast.makeText(ctx, "Choose at least one file to send.", Toast.LENGTH_SHORT).show()
            }
        }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.send_selected)) }
    }
}

@Composable
private fun History(vm: ShareViewModel, ads: AdManager, adFree: Boolean) {
    val history by vm.history.collectAsState()
    Scaffold { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp)) {
            itemsIndexed(history, key = { _, it -> it.id }) { index, item ->
                if (index > 0 && index % 8 == 0) NativeSlot(!adFree && ads.canRequestAds)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.displayName} · ${item.status}")
                    TextButton(onClick = { vm.toggleFavorite(item) }) { Text(stringResource(R.string.favorites)) }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPage(section: String, vm: ShareViewModel, ads: AdManager, adFree: Boolean) {
    val history by vm.history.collectAsState()
    val apps = remember(section) { if (section == "apps") vm.installedApps() else emptyList() }
    val appContext = LocalContext.current
    val (free, total) = vm.storage()
    val title = section.replaceFirstChar { it.uppercase() }
    val rows = when (section) {
        "received" -> history.filter { it.direction.equals("RECEIVE", true) && it.status != "DELETED" }
        "sent" -> history.filter { it.direction.equals("SEND", true) && it.status != "DELETED" }
        "deleted" -> history.filter { it.status == "DELETED" }
        "music" -> history.filter { it.status != "DELETED" && (it.mime.startsWith("audio/") || it.displayName.substringAfterLast('.', "").lowercase() in setOf("mp3","m4a","aac","wav","ogg","flac")) }
        "pictures" -> history.filter { it.status != "DELETED" && (it.mime.startsWith("image/") || it.displayName.substringAfterLast('.', "").lowercase() in setOf("jpg","jpeg","png","gif","webp","heic")) }
        "videos" -> history.filter { it.status != "DELETED" && (it.mime.startsWith("video/") || it.displayName.substringAfterLast('.', "").lowercase() in setOf("mp4","mkv","mov","webm","3gp")) }
        else -> emptyList()
    }
    Scaffold { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                if (section == "memory") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Device storage", style = MaterialTheme.typography.titleMedium)
                            Text("${free / 1_048_576} MB available")
                            Text("${(total - free).coerceAtLeast(0) / 1_048_576} MB used")
                        }
                    }
                }
                if (section == "apps") {
                    Text("Installed apps", style = MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                        userScrollEnabled = true,
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        items(apps, key = { it.second }) { app ->
                            val icon = remember(app.second) { runCatching { appContext.packageManager.getApplicationIcon(app.second).toBitmap().asImageBitmap() }.getOrNull() }
                            Card(Modifier.padding(4.dp)) {
                                Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (icon != null) Image(bitmap = icon, contentDescription = app.first, modifier = Modifier.size(42.dp))
                                    Text(app.first, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                if (section == "deleted") Text("Long-press a transfer record to move it here. Restore it or remove the record from the list.")
                else if (section != "memory" && section != "apps") Text("Long-press a transfer record to move it to Deleted.")
            }
            itemsIndexed(rows, key = { _, item -> item.id }) { _, item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(onClick = {}, onLongClick = { if (section != "deleted") vm.moveToDeleted(item) })) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("${item.bytes / 1024} KB · ${item.peerName}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (section == "deleted") {
                                TextButton(onClick = { vm.restoreFromDeleted(item) }) { Text("Restore") }
                                TextButton(onClick = { vm.permanentlyDelete(item) }) { Text("Remove record") }
                            } else {
                                TextButton(onClick = { vm.toggleFavorite(item) }) { Text("Favorite") }
                                TextButton(onClick = { vm.moveToDeleted(item) }) { Text("Move to Deleted") }
                            }
                        }
                    }
                }
            }
            if (rows.isEmpty() && section != "memory" && section != "apps") item { Text("No items in this section yet.") }
        }
    }
}

@Composable
private fun Settings(nav: NavHostController, vm: ShareViewModel, ads: AdManager, activity: Activity) {
    val settings by vm.settings.collectAsState(initial = null)
    val s = settings ?: return
    val ctx = LocalContext.current
    val (freeBytes, totalBytes) = vm.storage()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BrandHeader() }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This device", style = MaterialTheme.typography.titleMedium)
                    Text(android.os.Build.MODEL)
                    Text("Android ${android.os.Build.VERSION.RELEASE}")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Transfer", style = MaterialTheme.typography.titleMedium)
                    Text("Nearby sharing and connected-device transfers")
                    SwitchRow(stringResource(R.string.haptics), s.haptics) { vm.haptics(it) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("system", "light", "dark").forEach { mode ->
                            FilterChip(selected = s.themeMode == mode, onClick = { vm.theme(mode) }, label = { Text(mode) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("royal", "ocean", "ember", "violet").forEach { accent ->
                            FilterChip(selected = s.accent == accent, onClick = { vm.accent(accent) }, label = { Text(accent) })
                        }
                    }
                    SwitchRow(stringResource(R.string.reduce_motion), s.reduceMotion) { vm.motion(it) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium)
                    SwitchRow(stringResource(R.string.notifications), s.notifications) { vm.notif(it) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Storage and data", style = MaterialTheme.typography.titleMedium)
                    Text("${freeBytes / 1_048_576} MB free of ${totalBytes / 1_048_576} MB")
                    TextButton(onClick = { nav.navigate("history") }) { Text(stringResource(R.string.history)) }
                    TextButton(onClick = { vm.clearHistory() }) { Text(stringResource(R.string.clear_data)) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ads and rewards", style = MaterialTheme.typography.titleMedium)
                    Text("Watch five rewarded videos to unlock 24 hours without ads (${s.adFreeRewardCount}/5).")
                    Button(onClick = { ads.showRewarded(activity) { vm.rewardAdFreeProgress() } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Watch rewarded video")
                    }
                    OutlinedButton(onClick = { ads.showRewarded(activity) { vm.premiumDay() } }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.unlock_premium))
                    }
                    TextButton(onClick = { ads.privacyOptions(activity) }) { Text(stringResource(R.string.privacy_options)) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Legal and support", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS))) }) { Text(stringResource(R.string.terms)) }
                    TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY))) }) { Text(stringResource(R.string.privacy)) }
                    TextButton(onClick = {
                        val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:bqsqwadtechnologies@bqsqwadai.com"))
                        ctx.startActivity(email)
                    }) { Text(stringResource(R.string.feedback)) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
                    Text(stringResource(R.string.company))
                    Text(stringResource(R.string.licenses) + ": AndroidX, Kotlin, Hilt, Room, ML Kit, Nearby, ZXing, AdMob.")
                    TextButton(onClick = { nav.navigate("more") }) { Text(stringResource(R.string.more_apps)) }
                    if (BuildConfig.DEBUG) TextButton(onClick = { nav.navigate("debug") }) { Text(stringResource(R.string.debug_ads)) }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun Gated(settings: UserSettings, ads: AdManager, activity: Activity, vm: ShareViewModel, which: String, content: @Composable () -> Unit) {
    val open = settings.premiumUntil > System.currentTimeMillis()
    if (open) content() else Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.unlock_premium))
        Button(onClick = { ads.showRewarded(activity) { vm.premiumDay() } }) { Text(stringResource(R.string.unlock_premium)) }
    }
}

@Composable
private fun Clone(vm: ShareViewModel) {
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.clone), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.privacy_note))
        Text("Photos, videos, music, documents and the app list move only after the other phone accepts. Verification ${vm.verification}.")
        Button(onClick = { vm.nearby.startDiscovery() }) { Text(stringResource(R.string.looking)) }
    }
}

@Composable
private fun Computer(vm: ShareViewModel) {
    var url by remember { mutableStateOf<String?>(null) }
    val pin = remember { vm.sessionCode }
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.computer), style = MaterialTheme.typography.titleLarge)
        Text("${stringResource(R.string.pin_label)} $pin")
        Button(onClick = {
            val apk = vm.ownApk()
            url = vm.startWeb(listOf(apk), pin) + "/?pin=$pin"
        }) { Text(stringResource(R.string.continue_label)) }
        url?.let {
            Text(it)
            Image(bitmap = qrBitmap(it).asImageBitmap(), contentDescription = it, modifier = Modifier.size(200.dp))
        }
        OutlinedButton(onClick = { vm.stopWeb(); url = null }) { Text(stringResource(R.string.disconnect)) }
    }
}

@Composable
private fun Cleaner(vm: ShareViewModel) {
    val history by vm.history.collectAsState()
    val dupes = history.groupBy { it.displayName }.filter { it.value.size > 1 }
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.cleaner), style = MaterialTheme.typography.titleLarge)
        Text("Large items and repeated names in your transfer history.")
        history.filter { it.bytes > 50_000_000 }.forEach { Text(it.displayName) }
        dupes.forEach { (name, rows) -> Text("$name appears ${rows.size} times") }
        if (history.isEmpty()) Text(stringResource(R.string.empty_recent))
    }
}

@Composable
private fun Favorites(vm: ShareViewModel) {
    val fav by vm.favorites.collectAsState()
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.favorites), style = MaterialTheme.typography.titleLarge)
        if (fav.isEmpty()) Text(stringResource(R.string.empty_recent))
        fav.forEach { Text(it.displayName) }
    }
}

@Composable
private fun Chat(vm: ShareViewModel) {
    var text by remember { mutableStateOf("") }
    val connected by vm.nearby.connected.collectAsState()
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.chat), style = MaterialTheme.typography.titleLarge)
        Text(if (connected.isEmpty()) stringResource(R.string.not_found) else connected.joinToString())
        androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            connected.forEach { vm.sendText(it, text) }
            text = ""
        }) { Text(stringResource(R.string.group_send)) }
    }
}

@Composable
private fun More() {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandHeader()
        Text("About BQ Sqwad Share", style = MaterialTheme.typography.headlineSmall)
        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text("Built and published by BQ Sqwad Technologies.")
        Text("Share photos, videos, music, documents and apps with nearby devices.")
        Text("Our signature: BQ Sqwad Technologies")
        Text("Open-source components: AndroidX, Kotlin, Hilt, ML Kit, Google Nearby Connections, ZXing and Google Mobile Ads.")
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS))) }) { Text("Terms of Service") }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY))) }) { Text("Privacy Policy") }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:hello@bqsqwadai.com"))) }) { Text("Contact BQ Sqwad Technologies") }
    }
}

@Composable
private fun Debug(ads: AdManager) {
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.debug_ads), style = MaterialTheme.typography.titleLarge)
        Text("consent=${ads.consentResolved} canRequest=${ads.canRequestAds}")
        Text("banner=home,history native=every 8 interstitial=after 3 tasks rewarded=settings app-open=4h")
        Text("interstitialLoaded=${ads.interstitialLoaded} rewardedLoaded=${ads.rewardedLoaded} appOpenLoaded=${ads.appOpenLoaded}")
        BannerSlot(true)
        NativeSlot(true)
    }
}

private fun qrBitmap(text: String): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
    val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    for (x in 0 until 512) for (y in 0 until 512) {
        bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
    }
    return bmp
}
