
package com.bqsqwad.share.ui

import android.app.Activity
import android.content.Intent
import android.content.ComponentName
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.fragment.app.FragmentActivity
import com.bqsqwad.share.vault.PrivateVaultScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bqsqwad.share.BuildConfig
import com.bqsqwad.share.R
import com.bqsqwad.share.ShareViewModel
import com.bqsqwad.share.data.TransferEntity
import com.bqsqwad.share.ads.AdManager
import com.bqsqwad.share.ads.BannerSlot
import com.bqsqwad.share.ads.NativeSlot
import com.bqsqwad.share.media.SharePlaybackService
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.delay

private const val TERMS = "https://bqsqwadtechnologies.github.io/bq-sqwad-share-app/terms.html"
private const val PRIVACY = "https://bqsqwadtechnologies.github.io/bq-sqwad-share-app/privacy.html"

@Composable
fun ShareAppRoot(activity: Activity, vm: ShareViewModel, settings: UserSettings?, ads: AdManager) {
    val dark = settings?.themeMode == "dark"
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
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)
                        ) {
                            BrandHeader()
                            Text("SHARE", style = MaterialTheme.typography.labelLarge)
                            listOf(
                                "Send" to "send",
                                "Scan QR" to "scan",
                                "Receive" to "receive",
                                "Computer/iPhone" to "computer",
                                "Phone Clone" to "clone",
                            ).forEach { (label, route) ->
                                NavigationDrawerItem(
                                    label = { Text(label) },
                                    selected = currentRoute == route,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        nav.navigate(route) { launchSingleTop = true }
                                    },
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("BROWSE", style = MaterialTheme.typography.labelLarge)
                            listOf(
                                "Files" to "files",
                                "Apps" to "library/apps",
                                "Photos" to "media/photos",
                                "Videos" to "media/videos",
                                "Music" to "media/music",
                                "Documents" to "files",
                                "Received" to "library/received",
                                "Sent" to "library/sent",
                            ).forEach { (label, route) ->
                                NavigationDrawerItem(
                                    label = { Text(label) },
                                    selected = currentRoute == route,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        nav.navigate(route) { launchSingleTop = true }
                                    },
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("TOOLS", style = MaterialTheme.typography.labelLarge)
                            listOf(
                                "Storage Intelligence" to "storage-intelligence",
                                "Clean" to "cleaner",
                                "Favorites" to "favorites",
                                "Chat" to "chat",
                                "Folder Transfer" to "folder-transfer",
                                "Device Hub" to "device-hub",
                                "Team Sharing" to "team-sharing",
                                "Private Vault" to "private-vault",
                            ).forEach { (label, route) ->
                                NavigationDrawerItem(
                                    label = { Text(label) },
                                    selected = currentRoute == route,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        nav.navigate(route) { launchSingleTop = true }
                                    },
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("MORE", style = MaterialTheme.typography.labelLarge)
                            listOf(
                                "Settings" to "settings",
                                "About" to "more",
                            ).forEach { (label, route) ->
                                NavigationDrawerItem(
                                    label = { Text(label) },
                                    selected = currentRoute == route,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        nav.navigate(route) { launchSingleTop = true }
                                    },
                                )
                            }
                        }
                    }
                },
            ) {
                Scaffold(
                    bottomBar = {
                        Column {
                            GlobalMusicBar()
                            // Keep ads on browsing/information pages only. Never show them
                            // during pairing, QR scanning, sending, receiving, or transfer flows.
                            val adVisibleRoutes = setOf(
                                "home", "files", "history", "settings", "favorites", "more",
                                "library/{section}", "media/{kind}",
                            )
                            BannerSlot(
                                enabled = !adFree && ads.canRequestAds && currentRoute in adVisibleRoutes,
                                modifier = Modifier.navigationBarsPadding(),
                            )
                        }
                    },
                ) { adPadding ->
                    NavHost(
                        nav,
                        startDestination = "home",
                        modifier = Modifier.padding(adPadding),
                    ) {
                        composable("home") { Home(nav, vm, ads, adFree) { scope.launch { drawerState.open() } } }
                        composable("files") { FilesBrowser(nav, vm) }
                        composable("send") { Send(nav, vm) }
                        composable("scan") {
                            QrScanner { code ->
                                vm.setScannedReceiveCode(code)
                                nav.navigate("send") { popUpTo("scan") { inclusive = true } }
                            }
                        }
                        composable("receive") { Receive(vm) }
                        composable("picker") { Picker(nav, vm, ads, adFree) }
                        composable("history") { History(vm, ads, adFree) }
                        composable("library/{section}") { entry ->
                            FilesBrowser(nav, vm, initialTab = entry.arguments?.getString("section").orEmpty())
                        }
                        composable("media/{kind}") { entry ->
                            MediaLibraryScreen(initialKind = entry.arguments?.getString("kind").orEmpty())
                        }
                        composable("settings") { Settings(nav, vm, ads, activity) }
                        composable("clone") { Gated(settings, ads, activity, vm, "clone") { Clone(vm) } }
                        composable("computer") { Gated(settings, ads, activity, vm, "computer") { Computer(vm) } }
                        composable("storage-intelligence") { StorageIntelligenceScreen { nav.navigate("cleaner") } }
                        composable("cleaner") { Cleaner(vm) }
                        composable("folder-transfer") { FolderTransferScreen(nav, vm) }
                        composable("device-hub") { DeviceHubScreen(nav, vm) }
                        composable("private-vault") {
                            val biometricActivity = activity as? FragmentActivity
                            if (biometricActivity == null) Text("Private Vault requires a compatible Android activity.")
                            else PrivateVaultScreen(biometricActivity)
                        }
                        composable("favorites") { Favorites(vm) }
                        composable("chat") { Chat(vm) }
                        composable("team-sharing") { TeamSharingScreen(vm) }
                        composable("more") { More() }
                        composable("debug") { Debug(ads) }
                    }
                }
            }
        }
    }
}
@Composable
private fun GlobalMusicBar() {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf<String?>(null) }
    val future = remember(context) {
        val token = SessionToken(context, ComponentName(context, SharePlaybackService::class.java))
        MediaController.Builder(context, token).buildAsync()
    }
    DisposableEffect(future) {
        future.addListener({ player = runCatching { future.get() }.getOrNull() }, MoreExecutors.directExecutor())
        onDispose { player = null; MediaController.releaseFuture(future) }
    }
    DisposableEffect(player) {
        val active = player ?: return@DisposableEffect onDispose { }
        fun refresh() {
            playing = active.isPlaying
            title = active.currentMediaItem?.mediaMetadata?.title?.toString()
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { refresh() }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { refresh() }
            override fun onPlaybackStateChanged(playbackState: Int) { refresh() }
        }
        active.addListener(listener)
        refresh()
        onDispose { active.removeListener(listener) }
    }
    val active = player
    if (active != null && !title.isNullOrBlank()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(Modifier.size(34.dp), "BQ Sqwad Share")
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Now playing", style = MaterialTheme.typography.labelSmall)
                    Text(title ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
                TextButton(onClick = { if (active.isPlaying) active.pause() else active.play() }) { Text(if (playing) "Pause" else "Play") }
                TextButton(onClick = { active.stop(); active.clearMediaItems(); title = null; playing = false }) { Text("Stop") }
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
        BrandLogo(Modifier.size(56.dp), stringResource(R.string.app_name))
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.company))
        }
    }
}

@Composable
private fun Home(nav: NavHostController, vm: ShareViewModel, ads: AdManager, adFree: Boolean, onMenu: () -> Unit) {
    val history by vm.history.collectAsState()
    var search by remember { mutableStateOf("") }
    var quickActionsExpanded by remember { mutableStateOf(false) }
    var systemAppsExpanded by remember { mutableStateOf(false) }
    var systemApps by remember { mutableStateOf<List<ShareAppItem>>(emptyList()) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { systemApps = withContext(Dispatchers.IO) { MediaRepository.loadApps(context, true) } }
    val tiles = listOf(
        Triple("Send", "send", Icons.Filled.Send),
        Triple("Receive", "receive", Icons.Filled.QrCodeScanner),
        Triple("Photos", "media/photos", Icons.Filled.PhotoLibrary),
        Triple("Videos", "media/videos", Icons.Filled.VideoLibrary),
        Triple("Music", "media/music", Icons.Filled.MusicNote),
        Triple("Files", "files", Icons.Filled.FolderOpen),
        Triple("Apps", "library/apps", Icons.Filled.Apps),
        Triple("History", "history", Icons.Filled.History),
    ).filter { it.first.contains(search.trim(), ignoreCase = true) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(58.dp).clickable { quickActionsExpanded = true }) {
                    BrandLogo(Modifier.fillMaxSize(), "BQ Sqwad Share")
                }
                DropdownMenu(
                    expanded = quickActionsExpanded,
                    onDismissRequest = { quickActionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("↑  Send") },
                        onClick = {
                            quickActionsExpanded = false
                            nav.navigate("send") { launchSingleTop = true }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("↓  Receive") },
                        onClick = {
                            quickActionsExpanded = false
                            nav.navigate("receive") { launchSingleTop = true }
                        },
                    )
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("BQ Sqwad Share", style = MaterialTheme.typography.titleLarge)
                    Text("Share everything. Simply.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onMenu) { Text("Menu") }
            }
        }
        itemsIndexed(tiles.chunked(4)) { _, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, route, icon) ->
                    Card(
                        Modifier.weight(1f).height(112.dp).clickable {
                            nav.navigate(route) { launchSingleTop = true }
                        },
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            androidx.compose.material3.Icon(
                                icon,
                                contentDescription = label,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search apps and sections") },
                placeholder = { Text("Photos, music, videos, files…") },
            )
        }
        if (search.isBlank()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("System apps", style = MaterialTheme.typography.titleMedium)
                                Text("${systemApps.size} built-in apps", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { systemAppsExpanded = !systemAppsExpanded }) {
                                Text(if (systemAppsExpanded) "Hide" else "Show")
                            }
                        }
                        if (systemAppsExpanded) {
                            systemApps.chunked(4).forEach { appRow ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    appRow.forEach { app ->
                                        val appIcon = remember(app.packageName) {
                                            runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap() }.getOrNull()
                                        }
                                        Column(
                                            Modifier.weight(1f).height(92.dp).clickable {
                                                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launchIntent -> context.startActivity(launchIntent) }
                                            }.padding(4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            if (appIcon != null) Image(appIcon, contentDescription = app.label, modifier = Modifier.size(34.dp))
                                            else androidx.compose.material3.Icon(Icons.Filled.PhoneAndroid, contentDescription = app.label, modifier = Modifier.size(34.dp))
                                            Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                        }
                                    }
                                    repeat(4 - appRow.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Recent transfers", style = MaterialTheme.typography.titleLarge)
            }
            if (history.isEmpty()) item { Text(stringResource(R.string.empty_recent)) }
            itemsIndexed(history.take(5), key = { _, item -> item.id }) { _, item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            if (item.direction.equals("SEND", true)) Icons.Filled.Send else Icons.Filled.FolderOpen,
                            contentDescription = null,
                        )
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("${item.direction} · ${item.status}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else if (tiles.isEmpty()) {
            item { Text("No matching section found.") }
        }
    }
}

@Composable
private fun Send(nav: NavHostController, vm: ShareViewModel) {
    val peers by vm.nearby.peers.collectAsState()
    val connected by vm.nearby.connected.collectAsState()
    val connecting by vm.nearby.connecting.collectAsState()
    val scannedCode by vm.scannedReceiveCode.collectAsState()
    var receiveCode by remember { mutableStateOf(scannedCode) }
    LaunchedEffect(scannedCode) { if (scannedCode.isNotBlank()) receiveCode = scannedCode }
    val matchingPeers = peers
    val discoveryError by vm.nearby.discoveryError.collectAsState()
    val requiredPermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.NEARBY_WIFI_DEVICES)
        } else if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val missing = requiredPermissions.filter { granted[it] != true }
        permissionDenied = missing.isNotEmpty()
        if (missing.isEmpty()) vm.nearby.startDiscovery()
    }
    LaunchedEffect(Unit) {
        perms.launch(requiredPermissions)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.looking), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.privacy_note))
        if (permissionDenied) {
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nearby permission is missing")
                    Text("Allow Bluetooth scan and connection access so this phone can find other BQ Sqwad Share devices.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { perms.launch(requiredPermissions) }) { Text("Grant permissions and retry") }
                }
            }
        }
        discoveryError?.let { error ->
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nearby discovery could not start")
                    Text(error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.nearby.startDiscovery() }) { Text("Retry discovery") }
                }
            }
        }
        androidx.compose.material3.OutlinedTextField(
            value = receiveCode,
            onValueChange = { receiveCode = it.filter(Char::isDigit).take(6) },
            label = { Text("Enter 6-digit receive code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (connecting.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Column {
                        Text("Connecting to device…", style = MaterialTheme.typography.titleSmall)
                        Text("Keep both devices nearby while the connection is established.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (connected.isNotEmpty()) {
            Text("${connected.size} device(s) connected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        if (matchingPeers.isEmpty()) Text("Searching for nearby devices… Keep both devices close and Bluetooth/Wi-Fi enabled.", modifier = Modifier.padding(vertical = 12.dp))
        if (receiveCode.length != 6) {
            Text("Enter or scan the receiver’s six-digit code before connecting.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        }
        matchingPeers.forEach { peer ->
            val isConnected = peer.endpointId in connected
            val isConnecting = peer.endpointId in connecting
            Card(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(
                    enabled = receiveCode.length == 6 && !isConnected && !isConnecting,
                ) {
                    val displayName = "BQ Sqwad Share code $receiveCode"
                    vm.nearby.requestConnection(peer.endpointId, displayName)
                },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(peer.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                isConnected -> "Connected · ready to share"
                                isConnecting -> "Connecting…"
                                else -> "Nearby device · tap to connect"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    when {
                        isConnected -> TextButton(onClick = { nav.navigate("picker") }) { Text("Continue") }
                        isConnecting -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        else -> Text("Connect", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Button(onClick = { nav.navigate("scan") }, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.scan_qr)) }
    }
}

@Composable
private fun Receive(vm: ShareViewModel) {
    val context = LocalContext.current
    val incoming by vm.nearby.incoming.collectAsState()
    val connecting by vm.nearby.connecting.collectAsState()
    val incomingMessage by vm.incomingTransferMessage.collectAsState()
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
        if (connecting.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                    Column {
                        Text("Connecting to sender…", style = MaterialTheme.typography.titleSmall)
                        Text("Keep this screen open while the devices establish their connection.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (incomingMessage.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(incomingMessage, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.dismissIncomingTransferMessage() }) { Text("Dismiss") }
                }
            }
        }
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
    var confirmGroupSend by remember { mutableStateOf(false) }
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
        if (connected.size > 1) {
            OutlinedButton(
                onClick = {
                    if (picked.value.isNotEmpty()) vm.queueFiles(picked.value)
                    if (queued.isNotEmpty() || queuedLocal.isNotEmpty() || picked.value.isNotEmpty()) {
                        confirmGroupSend = true
                    } else {
                        Toast.makeText(ctx, "Choose at least one file to send.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send to all ${connected.size} connected devices") }
        }
    }
    if (confirmGroupSend) {
        AlertDialog(
            onDismissRequest = { confirmGroupSend = false },
            title = { Text("Send to multiple devices?") },
            text = { Text("The selected files will be sent separately to each of the ${connected.size} connected devices. This may use more data and storage.") },
            confirmButton = {
                Button(onClick = {
                    confirmGroupSend = false
                    vm.sendQueuedFilesToAll()
                    ads.onTaskCompleted(ctx as Activity, adFree)
                    nav.popBackStack()
                }) { Text("Send to all") }
            },
            dismissButton = { TextButton(onClick = { confirmGroupSend = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun History(vm: ShareViewModel, ads: AdManager, adFree: Boolean) {
    val history by vm.history.collectAsState()
    val connected by vm.nearby.connected.collectAsState()
    val peers by vm.nearby.peers.collectAsState()
    var retryItem by remember { mutableStateOf<TransferEntity?>(null) }
    Scaffold { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp)) {
            item {
                val queuedCount = history.count { it.direction.equals("SEND", true) && it.status == "QUEUED" }
                val activeCount = history.count { it.direction.equals("SEND", true) && it.status == "SENDING" }
                val waitingCount = history.count { it.direction.equals("SEND", true) && it.status == "WAITING_FOR_DEVICE" }
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Transfer activity", style = MaterialTheme.typography.titleMedium)
                        Text("$activeCount transferring · $queuedCount queued · $waitingCount waiting to retry")
                        Text("Multiple files are sent in parallel in small batches to keep the connection responsive.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            itemsIndexed(history, key = { _, it -> it.id }) { _, item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("${item.status} · ${MediaRepository.formatBytes(item.bytesDone)} / ${MediaRepository.formatBytes(item.bytes)}")
                        Text("Device: ${item.peerName}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.toggleFavorite(item) }) { Text(stringResource(R.string.favorites)) }
                            if (item.direction.equals("SEND", true) &&
                                item.status in setOf("SENDING", "QUEUED", "WAITING_FOR_DEVICE")
                            ) {
                                TextButton(onClick = { vm.cancelTransfer(item) }) { Text("Cancel transfer") }
                            }
                            if (item.direction.equals("SEND", true) &&
                                item.status in setOf("FAILED", "WAITING_FOR_DEVICE", "FAILED_SOURCE_MISSING")
                            ) {
                                TextButton(onClick = { retryItem = item }) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }
    retryItem?.let { item ->
        AlertDialog(
            onDismissRequest = { retryItem = null },
            title = { Text("Choose the receiving device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Choose the device that should receive this file. Select the original receiver to continue its saved checkpoint.")
                    if (connected.isEmpty()) {
                        Text("No devices are connected. Connect to the intended receiver first.")
                    } else {
                        connected.forEach { endpointId ->
                            val label = peers.firstOrNull { it.endpointId == endpointId }?.name ?: "Nearby device " + endpointId.take(6)
                            TextButton(onClick = {
                                vm.retryTransfer(item, endpointId)
                                retryItem = null
                            }) { Text(label) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { retryItem = null }) { Text("Close") } },
        )
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
    val connectedDevices by vm.nearby.connected.collectAsState()
    val discoveredPeers by vm.nearby.peers.collectAsState()
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
                    Text("Relay participation", style = MaterialTheme.typography.titleMedium)
                    Text("This setting is an explicit permission for this phone to help forward other people's encrypted transfers when a working relay network is available. It is off by default and you can turn it off at any time.")
                    SwitchRow("Allow this device to help relay other people's transfers", s.relayParticipationEnabled) { vm.relayParticipation(it) }
                    Text(
                        if (s.relayParticipationEnabled)
                            "Permission saved. Relay forwarding is not active yet: this app currently has no connected multi-hop relay transport. No background relay or sensor monitoring is running."
                        else
                            "Relay participation is off. This phone will not volunteer to forward other people's files.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Sending your own file does not silently enable third-party relay participation. When long-distance routing is implemented, your own transfer will use available relay hops only while your send session is active.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connected devices · ${connectedDevices.size}", style = MaterialTheme.typography.titleMedium)
                    if (connectedDevices.isEmpty()) {
                        Text("No devices are connected.")
                    } else {
                        connectedDevices.forEach { endpointId ->
                            val deviceName = discoveredPeers.firstOrNull { it.endpointId == endpointId }?.name ?: "Nearby device"
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(deviceName, style = MaterialTheme.typography.titleSmall)
                                    Text("Connected securely for this session", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { vm.nearby.disconnect(endpointId) }) { Text("Disconnect") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    BrandLogo(Modifier.size(72.dp), "Current BQ Sqwad Share logo")
                    Text("Brand assets are stored separately: branding/app-identity/app-icon.png for the app icon and branding/company-identity/company-icon.png for the company logo. Replace the appropriate source file and regenerate branding assets when either identity changes.")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("light", "dark").forEach { mode ->
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
                    Text("About, legal and support", style = MaterialTheme.typography.titleMedium)
                    Text("Legal information and support.")
                    Button(onClick = { nav.navigate("more") }) { Text("Open About") }
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

private fun clearCacheSafely(directory: File) {
    directory.listFiles()?.forEach { child ->
        runCatching {
            if (child.isDirectory) {
                clearCacheSafely(child)
                if (child.listFiles()?.isEmpty() == true) child.delete()
            } else if (!child.name.startsWith("outgoing-")) {
                child.delete()
            }
        }
    }
}

@Composable
private fun Cleaner(vm: ShareViewModel) {
    val context = LocalContext.current
    val history by vm.history.collectAsState()
    val dupes = history.groupBy { it.displayName }.filter { it.value.size > 1 }
    val cacheRoots = remember(context) { listOfNotNull(context.cacheDir, context.externalCacheDir).distinctBy { it.absolutePath } }
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var cleaning by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var cleanedBytes by remember { mutableStateOf<Long?>(null) }
    var storedFiles by remember { mutableStateOf(emptyList<File>()) }
    val scope = rememberCoroutineScope()

    suspend fun scanCache(): Long = withContext(Dispatchers.IO) {
        cacheRoots.sumOf { root ->
            runCatching {
                if (!root.exists()) 0L
                else root.walkTopDown().filter { it.isFile && !it.name.startsWith("outgoing-") }.sumOf { file -> file.length() }
            }.getOrDefault(0L)
        }
    }

    suspend fun scanStoredFiles(): List<File> = withContext(Dispatchers.IO) {
        val root = context.getExternalFilesDir(null)
        if (root == null || !root.exists()) emptyList()
        else root.walkTopDown().filter { it.isFile && it.canRead() }.toList()
    }

    LaunchedEffect(cacheRoots) {
        scanning = true
        cacheBytes = scanCache()
        storedFiles = scanStoredFiles()
        scanning = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Deep Clean", style = MaterialTheme.typography.headlineSmall)
        Text("Scan temporary files created by BQ Sqwad Share and safely clear its app cache.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App cache", style = MaterialTheme.typography.titleMedium)
                Text(
                    cacheBytes?.let { MediaRepository.formatBytes(it) } ?: if (scanning) "Scanning…" else "Not scanned",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text("Only temporary cache belonging to this app is included. Photos, videos, downloads, received files, app data and other apps are never deleted.")
                if (scanning || cleaning) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !scanning && !cleaning,
                        onClick = {
                            scope.launch {
                                scanning = true
                                cacheBytes = scanCache()
                                storedFiles = scanStoredFiles()
                                scanning = false
                            }
                        },
                    ) { Text("Scan again") }
                    Button(
                        enabled = (cacheBytes ?: 0L) > 0L && !scanning && !cleaning,
                        onClick = { showConfirm = true },
                    ) { Text("Clean cache") }
                }
                cleanedBytes?.let {
                    Text("Cleaned ${MediaRepository.formatBytes(it)} of temporary cache.")
                }
            }
        }
        Text("Storage review", style = MaterialTheme.typography.titleMedium)
        Text("Large transfers and repeated names are shown as review suggestions only. Nothing in your personal storage is removed automatically.")
        history.filter { it.bytes > 50_000_000 }.take(20).forEach { item ->
            Text("• ${item.displayName} · ${MediaRepository.formatBytes(item.bytes)}")
        }
        dupes.entries.take(20).forEach { (name, rows) ->
            Text("• $name appears ${rows.size} times in transfer history")
        }
        Text("Largest files stored by this app", style = MaterialTheme.typography.titleMedium)
        if (storedFiles.isEmpty()) {
            Text("No app-stored files found.")
        } else {
            storedFiles.sortedByDescending { it.length() }.take(10).forEach { file ->
                Text("• ${file.name} · ${MediaRepository.formatBytes(file.length())}")
            }
        }
        Text("These are review-only details. Deep Clean does not remove received files, archives or extracted content.")
        if (history.isEmpty()) Text("No transfer history to review.")
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Clear temporary cache?") },
            text = { Text("This removes only files inside this app's cache folders. It does not delete received files, photos, videos, downloads, personal documents, or data belonging to other apps.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    scope.launch {
                        cleaning = true
                        val before = cacheBytes ?: scanCache()
                        withContext(Dispatchers.IO) {
                            cacheRoots.forEach { root ->
                                runCatching { clearCacheSafely(root) }
                            }
                        }
                        val after = scanCache()
                        cleanedBytes = (before - after).coerceAtLeast(0L)
                        cacheBytes = after
                        cleaning = false
                    }
                }) { Text("Clear app cache") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } },
        )
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandHeader()
        Text("About BQ Sqwad Share", style = MaterialTheme.typography.headlineSmall)
        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text("Share photos, videos, music, documents and apps with nearby devices.")
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS))) }) { Text("Terms of Service") }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY))) }) { Text("Privacy Policy") }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:bqsqwadtechnologies@bqsqwadai.com"))) }) { Text("Contact BQ Sqwad Technologies") }
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
