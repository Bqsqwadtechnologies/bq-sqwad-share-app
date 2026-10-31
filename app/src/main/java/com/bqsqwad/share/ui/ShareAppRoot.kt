
package com.bqsqwad.share.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import java.io.File

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
            NavHost(nav, startDestination = "home") {
                composable("home") { Home(nav, vm, ads, adFree) }
                composable("send") { Send(nav, vm) }
                composable("receive") { Receive(vm) }
                composable("picker") { Picker(nav, vm, ads, adFree) }
                composable("history") { History(vm, ads, adFree) }
                composable("library/{section}") { entry -> LibraryPage(entry.arguments?.getString("section").orEmpty(), vm, ads, adFree) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(nav: NavHostController, vm: ShareViewModel, ads: AdManager, adFree: Boolean) {
    val history by vm.history.collectAsState()
    val (free, total) = vm.storage()
    val ctx = LocalContext.current
    Scaffold(bottomBar = { BannerSlot(enabled = !adFree && ads.canRequestAds, modifier = Modifier.navigationBarsPadding()) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                BrandHeader()
                Text(stringResource(R.string.tagline))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { nav.navigate("send") }, modifier = Modifier.weight(1f).height(56.dp)) { Text(stringResource(R.string.send)) }
                    Button(onClick = { nav.navigate("receive") }, modifier = Modifier.weight(1f).height(56.dp)) { Text(stringResource(R.string.receive)) }
                }
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.storage), style = MaterialTheme.typography.titleLarge)
                        Text("${free / 1_048_576} MB free of ${total / 1_048_576} MB")
                        if (free < 200L * 1_048_576) Text(stringResource(R.string.low_storage))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleLarge)
            }
            if (history.isEmpty()) item { Text(stringResource(R.string.empty_recent), modifier = Modifier.padding(vertical = 8.dp)) }
            itemsIndexed(history.take(8), key = { _, it -> it.id }) { index, item ->
                if (index > 0 && index % 8 == 0) NativeSlot(!adFree && ads.canRequestAds)
                Text("${item.direction} · ${item.displayName}", modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { nav.navigate("history") }) { Text(stringResource(R.string.history)) }
                    TextButton(onClick = { nav.navigate("settings") }) { Text(stringResource(R.string.settings)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { nav.navigate("clone") }) { Text(stringResource(R.string.clone)) }
                    TextButton(onClick = { nav.navigate("computer") }) { Text(stringResource(R.string.computer)) }
                }
                Text("Your files", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("music", "pictures", "videos", "apps").forEach { section ->
                        TextButton(onClick = { nav.navigate("library/$section") }) { Text(section.replaceFirstChar { it.uppercase() }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("received", "sent", "deleted", "memory").forEach { section ->
                        TextButton(onClick = { nav.navigate("library/$section") }) { Text(section.replaceFirstChar { it.uppercase() }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { nav.navigate("cleaner") }) { Text(stringResource(R.string.cleaner)) }
                    TextButton(onClick = { nav.navigate("favorites") }) { Text(stringResource(R.string.favorites)) }
                    TextButton(onClick = { nav.navigate("chat") }) { Text(stringResource(R.string.chat)) }
                }
                Card(Modifier.fillMaxWidth().clickable { nav.navigate("more") }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.ad_label), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.home_cross))
                    }
                }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=com.bqsqwad.share")
                    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.invite)))
                }) { Text(stringResource(R.string.invite)) }
            }
        }
    }
}

@Composable
private fun Send(nav: NavHostController, vm: ShareViewModel) {
    val peers by vm.nearby.peers.collectAsState()
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) vm.nearby.startDiscovery()
    }
    LaunchedEffect(Unit) {
        perms.launch(arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ))
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.looking), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.privacy_note))
        if (peers.isEmpty()) Text(stringResource(R.string.not_found), modifier = Modifier.padding(vertical = 12.dp))
        peers.forEach { peer ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                vm.nearby.requestConnection(peer.endpointId, "BQ Sqwad Share")
                nav.navigate("picker")
            }) { Text(peer.name, modifier = Modifier.padding(16.dp)) }
        }
        Button(onClick = { nav.navigate("receive") }, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.scan_qr)) }
    }
}

@Composable
private fun Receive(vm: ShareViewModel) {
    val incoming by vm.nearby.incoming.collectAsState()
    LaunchedEffect(Unit) { vm.nearby.startAdvertising("BQ Sqwad Share") }
    val payload = "bqsqwad://share?code=${vm.sessionCode}&key=${vm.keyText}&v=${vm.verification}"
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.show_qr), style = MaterialTheme.typography.titleLarge)
        Image(bitmap = qrBitmap(payload).asImageBitmap(), contentDescription = stringResource(R.string.show_qr), modifier = Modifier.size(240.dp).padding(12.dp))
        Text(stringResource(R.string.qr_help))
        Text("${stringResource(R.string.manual_code)}: ${vm.sessionCode}")
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
                picked.value.forEach { Text(it.toString(), modifier = Modifier.padding(vertical = 4.dp)) }
            }
        }
        Button(onClick = {
            picked.value.forEach { uri -> vm.record(uri.lastPathSegment ?: "file", "application/octet-stream", 0, "SEND", "nearby", uri.toString()) }
            ads.onTaskCompleted(ctx as Activity, adFree)
            nav.popBackStack()
        }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.send_selected)) }
    }
}

@Composable
private fun History(vm: ShareViewModel, ads: AdManager, adFree: Boolean) {
    val history by vm.history.collectAsState()
    Scaffold(bottomBar = { BannerSlot(!adFree && ads.canRequestAds) }) { pad ->
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
    Scaffold(bottomBar = { BannerSlot(enabled = !adFree && ads.canRequestAds, modifier = Modifier.navigationBarsPadding()) }) { pad ->
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
                if (section == "deleted") Text("Long-press an item in another library to move it here. Restore it or permanently delete it.")
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
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { BrandHeader() }
        item { Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleLarge) }
        item {
            Row {
                listOf("system", "light", "dark").forEach { mode ->
                    FilterChip(selected = s.themeMode == mode, onClick = { vm.theme(mode) }, label = { Text(mode) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            Row {
                listOf("royal", "ocean", "ember", "violet").forEach { accent ->
                    FilterChip(selected = s.accent == accent, onClick = { vm.accent(accent) }, label = { Text(accent) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
        }
        item { SwitchRow(stringResource(R.string.haptics), s.haptics) { vm.haptics(it) } }
        item { SwitchRow(stringResource(R.string.reduce_motion), s.reduceMotion) { vm.motion(it) } }
        item { SwitchRow(stringResource(R.string.notifications), s.notifications) { vm.notif(it) } }
        item { Text(stringResource(R.string.language) + ": English") }
        item {
            Text("Watch five rewarded videos to unlock 24 hours without ads (${s.adFreeRewardCount}/5)")
            Button(onClick = { ads.showRewarded(activity) { vm.rewardAdFreeProgress() } }, modifier = Modifier.fillMaxWidth()) { Text("Watch rewarded video") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { ads.showRewarded(activity) { vm.premiumDay() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.unlock_premium)) }
        }
        item { TextButton(onClick = { ads.privacyOptions(activity) }) { Text(stringResource(R.string.privacy_options)) } }
        item { TextButton(onClick = { vm.clearHistory() }) { Text(stringResource(R.string.clear_data)) } }
        item { TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS))) }) { Text(stringResource(R.string.terms)) } }
        item { TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY))) }) { Text(stringResource(R.string.privacy)) } }
        item { TextButton(onClick = {
            val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:bqsqwadtechnologies@bqsqwadai.com"))
            ctx.startActivity(email)
        }) { Text(stringResource(R.string.feedback)) } }
        item { TextButton(onClick = { nav.navigate("more") }) { Text(stringResource(R.string.more_apps)) } }
        item { Text(stringResource(R.string.about) + " " + BuildConfig.VERSION_NAME + " · " + stringResource(R.string.company)) }
        item { Text(stringResource(R.string.licenses) + ": AndroidX, Kotlin, Hilt, Room, ML Kit, Nearby, ZXing, AdMob.") }
        if (BuildConfig.DEBUG) item { TextButton(onClick = { nav.navigate("debug") }) { Text(stringResource(R.string.debug_ads)) } }
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
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.more_apps), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.ad_label))
        Text("Edit config/more_apps.json to list sister apps under BQ Sqwad AI. Each card opens the Play listing by package name.")
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
