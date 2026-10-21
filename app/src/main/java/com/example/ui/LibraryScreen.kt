package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.LocalStorageManager
import com.example.Playlist
import com.example.Track
import com.example.ui.theme.NdotFont
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    favoriteTracks: List<Track>,
    currentlyPlayingTitle: String?,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit = { _, _ -> },
    onSelectArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var activeLibraryTab by remember { mutableStateOf("PLAYLISTS") }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val newId = java.util.UUID.randomUUID().toString()
                val savedIcon = LocalStorageManager.copyIconToApp(context, uri.toString(), newId)
                // Open create dialog with prefilled icon
                // Or create directly
                // Let's store picked icon uri in a state
            }
        }
    }

    var pickedIconUri by remember { mutableStateOf("") }

    val createPlaylistLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pickedIconUri = uri.toString()
            showCreateDialog = true
        }
    }

    fun refreshPlaylists() {
        playlists = LocalStorageManager.getPlaylists(context)
    }

    LaunchedEffect(Unit) {
        refreshPlaylists()
    }

    if (selectedPlaylist != null) {
        PlaylistDetailView(
            playlist = selectedPlaylist!!,
            onBack = {
                selectedPlaylist = null
                refreshPlaylists()
            },
            onPlayTrack = onPlayTrack,
            onPlayAll = onPlayAll
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0C10))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header: LIBRARY + Red Circle Create Playlist Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIBRARY",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 2.sp
                )

                // Thickish plus red circular button (opens create playlist popup)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD71921))
                        .clickable { showCreateDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val midX = size.width / 2f
                        val midY = size.height / 2f
                        val stroke = 3.2.dp.toPx()

                        // Horizontal arm
                        drawLine(
                            color = Color.White,
                            start = Offset(2f, midY),
                            end = Offset(size.width - 2f, midY),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                        // Vertical arm
                        drawLine(
                            color = Color.White,
                            start = Offset(midX, 2f),
                            end = Offset(midX, size.height - 2f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Tabs: PLAYLISTS / FAVORITES (Nothing Red highlight)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("PLAYLISTS", "FAVORITES").forEach { tab ->
                    val isSel = activeLibraryTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) Color(0xFFD71921) else Color(0xFF121318))
                            .border(1.dp, if (isSel) Color(0xFFD71921) else Color(0xFF262834), RoundedCornerShape(20.dp))
                            .clickable { activeLibraryTab = tab }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSel) Color.White else Color(0xFF7E838E),
                            fontSize = 11.5.sp,
                            fontFamily = NdotFont,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Content
            if (activeLibraryTab == "PLAYLISTS") {
                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "NO PLAYLISTS YET",
                                color = Color(0xFF666666),
                                fontSize = 13.sp,
                                fontFamily = NdotFont,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "TAP + TO CREATE ONE",
                                color = Color(0xFFD71921),
                                fontSize = 11.sp,
                                fontFamily = NdotFont,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistItemCard(
                                playlist = playlist,
                                onClick = { selectedPlaylist = playlist }
                            )
                        }
                    }
                }
            } else {
                // FAVORITES
                if (favoriteTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO FAVORITE TRACKS",
                            color = Color(0xFF666666),
                            fontSize = 13.sp,
                            fontFamily = NdotFont,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(favoriteTracks) { track ->
                            val isPlaying = currentlyPlayingTitle != null &&
                                    currentlyPlayingTitle.equals(track.title, ignoreCase = true)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isPlaying) Color(0xFF161822) else Color(0xFF0E0F14))
                                    .border(1.dp, if (isPlaying) Color(0xFFD71921) else Color(0xFF1E212B), RoundedCornerShape(12.dp))
                                    .clickable { onPlayTrack(track) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFD71921))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title.uppercase(),
                                        color = if (isPlaying) Color(0xFFD71921) else Color.White,
                                        fontSize = 13.5.sp,
                                        fontFamily = NdotFont,
                                        letterSpacing = 0.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = track.artist.uppercase(),
                                        color = Color(0xFF7E838E),
                                        fontSize = 10.5.sp,
                                        fontFamily = NdotFont,
                                        letterSpacing = 0.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = track.duration,
                                    color = Color(0xFF7E838E),
                                    fontSize = 11.sp,
                                    fontFamily = NdotFont,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // CREATE PLAYLIST DIALOG
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, desc, iconSource ->
                coroutineScope.launch {
                    val newId = java.util.UUID.randomUUID().toString()
                    val savedIcon = if (iconSource.isNotBlank()) {
                        LocalStorageManager.copyIconToApp(context, iconSource, newId)
                    } else ""

                    val newPlaylist = Playlist(
                        id = newId,
                        title = title,
                        description = desc,
                        iconPath = savedIcon,
                        tracks = emptyList()
                    )

                    val updated = playlists + newPlaylist
                    LocalStorageManager.savePlaylists(context, updated)
                    refreshPlaylists()
                    showCreateDialog = false
                    Toast.makeText(context, "PLAYLIST CREATED", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun PlaylistItemCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E0F14))
            .border(1.dp, Color(0xFF1E212B), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon (Supports Image & GIF!)
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF181920))
                .border(1.dp, Color(0xFF2C2F3C), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.iconPath.isNotBlank()) {
                AsyncImage(
                    model = playlist.iconPath,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = playlist.title.take(2).uppercase(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = NdotFont
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title.uppercase(),
                color = Color.White,
                fontSize = 14.5.sp,
                fontFamily = NdotFont,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "${playlist.tracks.size} TRACKS",
                color = Color(0xFF7E838E),
                fontSize = 11.sp,
                fontFamily = NdotFont,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    onBack: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var isDownloadingAll by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffsetX = 0f },
                    onDragEnd = {
                        if (dragOffsetX < -80f || dragOffsetX > 80f) {
                            onBack()
                        }
                        dragOffsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffsetX += dragAmount
                    }
                )
            }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top Bar: Back & Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF242630), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = playlist.title.uppercase(),
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = NdotFont,
                letterSpacing = 1.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Playlist Header: 1:1 round Icon + Title & Description
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF181920))
                    .border(1.dp, Color(0xFF2C2F3C), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.iconPath.isNotBlank()) {
                    AsyncImage(
                        model = playlist.iconPath,
                        contentDescription = playlist.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = playlist.title.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = NdotFont
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title.uppercase(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 1.sp
                )
                if (playlist.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playlist.description,
                        color = Color(0xFF7E838E),
                        fontSize = 11.sp,
                        fontFamily = NdotFont,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${playlist.tracks.size} TRACKS",
                    color = Color(0xFFD71921),
                    fontSize = 10.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 1.sp
                )
            }
        }

        // Action Buttons Row: PLAY, SHUFFLE, DOWNLOAD ALL
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play
            Button(
                onClick = { if (playlist.tracks.isNotEmpty()) onPlayAll(playlist.tracks, false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PLAY", fontFamily = NdotFont, fontSize = 11.sp)
            }

            // Shuffle
            Button(
                onClick = { if (playlist.tracks.isNotEmpty()) onPlayAll(playlist.tracks, true) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14151C), contentColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("SHUFFLE", fontFamily = NdotFont, fontSize = 11.sp)
            }

            // Download All
            Button(
                onClick = {
                    if (playlist.tracks.isNotEmpty() && !isDownloadingAll) {
                        isDownloadingAll = true
                        Toast.makeText(context, "DOWNLOADING PLAYLIST...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            for (t in playlist.tracks) {
                                LocalStorageManager.downloadTrackFile(context, t)
                            }
                            isDownloadingAll = false
                            Toast.makeText(context, "ALL DOWNLOADED", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921), contentColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isDownloadingAll) "DOWNLOADING..." else "DOWNLOAD ALL", fontFamily = NdotFont, fontSize = 9.5.sp)
            }
        }

        // Track List
        if (playlist.tracks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NO TRACKS IN THIS PLAYLIST",
                    color = Color(0xFF666666),
                    fontSize = 13.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 1.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(playlist.tracks) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0E0F14))
                            .border(1.dp, Color(0xFF1E212B), RoundedCornerShape(12.dp))
                            .clickable { onPlayTrack(track) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title.uppercase(),
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontFamily = NdotFont,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist.uppercase(),
                                color = Color(0xFF7E838E),
                                fontSize = 10.5.sp,
                                fontFamily = NdotFont,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = track.duration,
                            color = Color(0xFF7E838E),
                            fontSize = 11.sp,
                            fontFamily = NdotFont,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String, iconUrlOrPath: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var iconInput by remember { mutableStateOf("") }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) iconInput = uri.toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121318),
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CREATE PLAYLIST", color = Color.White, fontSize = 18.sp, fontFamily = NdotFont, letterSpacing = 1.sp)
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Cancel", tint = Color.White)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E202B))
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color(0xFFD71921)), contentAlignment = Alignment.Center) {
                            Text("+", color = Color.White, fontSize = 32.sp, fontFamily = NdotFont)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Supports Image or Gif", color = Color(0xFF666666), fontSize = 10.sp, fontFamily = NdotFont)
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("PLAYLIST TITLE", fontFamily = NdotFont, fontSize = 12.sp, color = Color(0xFF666666)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD71921),
                        unfocusedBorderColor = Color(0xFF262834),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("DESCRIPTION", fontFamily = NdotFont, fontSize = 12.sp, color = Color(0xFF666666)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD71921),
                        unfocusedBorderColor = Color(0xFF262834),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onCreate(title.trim(), description.trim(), iconInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921), contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CREATE", fontFamily = NdotFont, fontSize = 12.sp)
            }
        }
    )
}
