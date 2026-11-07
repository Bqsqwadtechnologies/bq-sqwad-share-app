package com.bqsqwad.share.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import coil.compose.AsyncImage
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.bqsqwad.share.media.SharePlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MediaLibraryScreen(initialKind: String = "music") {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(initialKind.takeIf { it == "photos" || it == "videos" || it == "music" } ?: "music") }
    var selectedPhoto by remember { mutableStateOf<ShareMediaItem?>(null) }
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

    fun requiredMediaPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) {
            when (kind) { "music" -> Manifest.permission.READ_MEDIA_AUDIO; "photos" -> Manifest.permission.READ_MEDIA_IMAGES; else -> Manifest.permission.READ_MEDIA_VIDEO }
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasMediaPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, requiredMediaPermission()) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionGranted = grants[requiredMediaPermission()] == true || hasMediaPermission()
    }

    fun requestMediaPermissions() {
        if (hasMediaPermission()) {
            permissionGranted = true
        } else {
            permissionLauncher.launch(arrayOf(requiredMediaPermission()))
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

    fun playFrom(index: Int, shuffleAll: Boolean = false) {
        val player = controller ?: return
        if (visibleMedia.isEmpty()) return
        val queue = visibleMedia.map { item ->
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
        player.setMediaItems(queue, index.coerceIn(0, queue.lastIndex), 0L)
        player.shuffleModeEnabled = shuffleAll
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.prepare()
        player.play()
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your media", style = MaterialTheme.typography.headlineMedium)
        Text("Enjoy your music and videos while file transfers continue in the background.")
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(when (kind) { "music" -> "Search music"; "photos" -> "Search photos"; else -> "Search videos" }) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        selectedPhoto?.let { photo ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AsyncImage(model = photo.uri, contentDescription = photo.name, modifier = Modifier.fillMaxWidth().height(260.dp))
                    Text(photo.name, style = MaterialTheme.typography.titleMedium)
                    Text(MediaRepository.formatBytes(photo.sizeBytes), style = MaterialTheme.typography.bodySmall)
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
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        activePlayer.currentMediaItem?.mediaMetadata?.title?.toString() ?: "BQ Sqwad Music Player",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { activePlayer.seekToPreviousMediaItem() }) { Text("Previous") }
                        Button(onClick = { if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play() }) {
                            Text(if (isPlaying) "Pause" else "Play")
                        }
                        OutlinedButton(onClick = { activePlayer.seekToNextMediaItem() }) { Text("Next") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        FilterChip(
                            selected = shuffle,
                            onClick = { activePlayer.shuffleModeEnabled = !activePlayer.shuffleModeEnabled },
                            label = { Text("Shuffle") },
                        )
                        FilterChip(
                            selected = repeatMode != Player.REPEAT_MODE_OFF,
                            onClick = {
                                activePlayer.repeatMode = when (activePlayer.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            },
                            label = { Text(when (repeatMode) {
                                Player.REPEAT_MODE_ALL -> "Repeat all"
                                Player.REPEAT_MODE_ONE -> "Repeat one"
                                else -> "Repeat off"
                            }) },
                        )
                    }
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
                    ) { Text("Playback speed · ${playbackSpeed}×") }
                }
            }
        }

        if (permissionGranted && visibleMedia.isNotEmpty()) {
            Text("${visibleMedia.size} ${when (kind) { "music" -> "songs"; "photos" -> "photos"; else -> "videos" }}", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(visibleMedia, key = { _, item -> item.key }) { index, item ->
                    Card(
                        Modifier.fillMaxWidth().clickable { if (kind == "photos") selectedPhoto = item else playFrom(index) },
                    ) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (kind == "photos") AsyncImage(model = item.uri, contentDescription = item.name, modifier = Modifier.size(76.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(MediaRepository.formatBytes(item.sizeBytes), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (index == currentIndex) "Playing" else "Play", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else if (permissionGranted && media.isNotEmpty() && visibleMedia.isEmpty()) {
            Text("No media matches your search.")
        }
    }
}
