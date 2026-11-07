package com.bqsqwad.share.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.bqsqwad.share.media.SharePlaybackService
import com.bqsqwad.share.R
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MediaLibraryScreen(initialKind: String = "music", compact: Boolean = false) {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(initialKind.takeIf { it == "photos" || it == "videos" || it == "music" } ?: "music") }
    var selectedPhoto by remember { mutableStateOf<ShareMediaItem?>(null) }
    var selectedVideo by remember { mutableStateOf<ShareMediaItem?>(null) }
    var media by remember { mutableStateOf<List<ShareMediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(-1) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var searchQuery by remember { mutableStateOf("") }
    var playbackSpeed by remember { mutableStateOf(1f) }

    fun hasMediaPermission(): Boolean = MediaPermissions.hasMedia(context, kind)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionGranted = hasMediaPermission()
    }

    fun requestMediaPermissions() {
        if (hasMediaPermission()) {
            permissionGranted = true
        } else {
            permissionLauncher.launch(MediaPermissions.list(kind))
        }
    }

    LaunchedEffect(kind) {
        selectedPhoto = null
        permissionGranted = hasMediaPermission()
        if (!permissionGranted) requestMediaPermissions()
    }

    LaunchedEffect(permissionGranted, kind) {
        if (permissionGranted) {
            loading = true
            media = withContext(Dispatchers.IO) { MediaRepository.load(context, kind) }
            loading = false
        }
    }

    val controllerFuture = remember(context) {
        val token = SessionToken(context, ComponentName(context, SharePlaybackService::class.java))
        MediaController.Builder(context, token).buildAsync()
    }
    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            { controller = runCatching { controllerFuture.get() }.getOrNull() },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(controllerFuture)
        }
    }

    DisposableEffect(controller) {
        val player = controller
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                shuffle = shuffleModeEnabled
            }
            override fun onRepeatModeChanged(repeatModeValue: Int) {
                repeatMode = repeatModeValue
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        currentIndex = player.currentMediaItemIndex
        onDispose { player.removeListener(listener) }
    }

    val visibleMedia = media.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }

    LaunchedEffect(kind) {
        if (kind != "music") controller?.pause()
    }

    fun shareMedia(item: ShareMediaItem) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share with Bluetooth or another app"))
    }

    fun switchMediaPage(direction: Int) {
        val pages = listOf("music", "videos", "photos")
        val next = (pages.indexOf(kind) + direction).coerceIn(0, pages.lastIndex)
        if (pages[next] != kind) kind = pages[next]
    }

    fun playFrom(index: Int, shuffleAll: Boolean = false) {
        val player = controller ?: return
        if (visibleMedia.isEmpty()) return
        val selectedItems = if (kind == "videos") listOf(visibleMedia[index.coerceIn(0, visibleMedia.lastIndex)]) else visibleMedia
        val queue = selectedItems.map { item ->
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.name)
                        .setArtist("BQ Sqwad Share")
                        .build(),
                )
                .build()
        }
        player.setMediaItems(queue, if (kind == "videos") 0 else index.coerceIn(0, queue.lastIndex), 0L)
        player.shuffleModeEnabled = shuffleAll
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.prepare()
        player.play()
    }

    Column(
        Modifier.fillMaxSize().then(
            if (compact) Modifier else Modifier.pointerInput(kind) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        if (drag < -72f) switchMediaPage(1)
                        else if (drag > 72f) switchMediaPage(-1)
                        drag = 0f
                    },
                )
            },
        ).padding(if (compact) 8.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
    ) {
        if (!compact) {
            Text(when (kind) { "music" -> "Music"; "videos" -> "Videos"; else -> "Photos" }, style = MaterialTheme.typography.headlineMedium)
            Text("Swipe left or right to switch between your media.")
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(when (kind) { "music" -> "Search music"; "photos" -> "Search photos"; else -> "Search videos" }) },
            )
        }
        if (!compact) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = kind == "music",
                onClick = { kind = "music" },
                label = { Text("Music") },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.MusicNote, null) },
            )
            FilterChip(
                selected = kind == "videos",
                onClick = { kind = "videos" },
                label = { Text("Videos") },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.VideoLibrary, null) },
            )
            FilterChip(
                selected = kind == "photos",
                onClick = { kind = "photos"; selectedPhoto = null },
                label = { Text("Photos") },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.PhotoLibrary, null) },
            )
        }

        if (!permissionGranted) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Allow access to the media you want to play.")
                    Button(onClick = { requestMediaPermissions() }) { Text("Grant media access") }
                }
            }
        } else if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (media.isEmpty()) {
            Text(when (kind) { "music" -> "No music was found on this device."; "photos" -> "No photos were found on this device."; else -> "No videos were found on this device." })
        } else if (kind != "photos") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { playFrom(0) }, enabled = controller != null) {
                    androidx.compose.material3.Icon(Icons.Filled.PlayArrow, null)
                    Text("Play all")
                }
                OutlinedButton(onClick = { playFrom(0, true) }, enabled = controller != null) {
                    androidx.compose.material3.Icon(Icons.Filled.Shuffle, null)
                    Text("Shuffle")
                }
            }
        }


        val activePlayer = controller
        if (activePlayer != null && kind == "videos") {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        player = activePlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { it.player = activePlayer },
            )
            OutlinedButton(
                onClick = {
                    playbackSpeed = when (playbackSpeed) {
                        1f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2f
                        else -> 1f
                    }
                    activePlayer.setPlaybackSpeed(playbackSpeed)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Video speed · ${playbackSpeed}×") }
        } else if (activePlayer != null && kind == "music") {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = "Music artwork",
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Now playing", style = MaterialTheme.typography.labelSmall)
                        Text(
                            activePlayer.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Choose a song",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                        )
                    }
                    TextButton(onClick = { activePlayer.seekToPreviousMediaItem() }) { Text("‹") }
                    Button(
                        onClick = { if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) { Text(if (isPlaying) "Pause" else "Play") }
                    TextButton(onClick = { activePlayer.seekToNextMediaItem() }) { Text("›") }
                }
            }
        }

        if (permissionGranted && visibleMedia.isNotEmpty()) {
            Text("${visibleMedia.size} ${when (kind) { "music" -> "songs"; "photos" -> "photos"; else -> "videos" }}", style = MaterialTheme.typography.titleMedium)
            if (kind == "photos") {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(visibleMedia.chunked(4), key = { _, row -> row.first().key }) { _, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { photo -> AsyncImage(model = photo.uri, contentDescription = photo.name, modifier = Modifier.weight(1f).height(96.dp).clickable { selectedPhoto = photo }) }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(visibleMedia, key = { _, item -> item.key }) { index, item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (kind == "videos") AsyncImage(model = item.uri, contentDescription = item.name, modifier = Modifier.size(width = 104.dp, height = 64.dp).clickable { selectedVideo = item; playFrom(index) }) else androidx.compose.material3.Icon(Icons.Filled.MusicNote, contentDescription = "Music", modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.clickable { if (kind == "videos") { selectedVideo = item; playFrom(index) } else playFrom(index) })
                                    Text(if (kind == "videos") MediaRepository.formatDuration(item.durationMs) else "Audio", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                TextButton(onClick = { shareMedia(item) }) { Text("Share") }
                            }
                        }
                    }
                }
            }
        } else if (permissionGranted && media.isNotEmpty() && visibleMedia.isEmpty()) {
            Text("No media matches your search.")
        }
        selectedPhoto?.let { photo ->
            Dialog(onDismissRequest = { selectedPhoto = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
                    AsyncImage(model = photo.uri, contentDescription = photo.name, modifier = Modifier.fillMaxWidth().weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { shareMedia(photo) }, modifier = Modifier.weight(1f)) { Text("Share") }
                        OutlinedButton(onClick = { selectedPhoto = null }, modifier = Modifier.weight(1f)) { Text("Close") }
                    }
                }
            }
        }
        selectedVideo?.let { video ->
            Dialog(onDismissRequest = { selectedVideo = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { viewContext -> PlayerView(viewContext).apply { player = controller; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } }, update = { it.player = controller })
                    OutlinedButton(onClick = { selectedVideo = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Text("Close") }
                }
            }
        }
    }
}
