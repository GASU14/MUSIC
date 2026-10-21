package com.example

import com.example.ui.FloatingNavBar
import com.example.ui.LibraryScreen
import com.example.ui.MainTab
import com.example.ui.SettingsScreen
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NdotFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.sin

data class Track(
    val title: String,
    val artist: String,
    val album: String = "",
    val duration: String = "3:30",
    val videoId: String = "",
    val thumbnailUrl: String = "",
    val audioUrl: String = "",
    val links: List<TrackLink> = emptyList()
)

enum class Screen {
    ARTIST_GRID,
    DISCOGRAPHY,
    PROJECT_TRACKS,
    FULL_PLAYER
}

class PlayerController {
    private var mediaPlayer: MediaPlayer? = null
    var isPlaying by mutableStateOf(false)
    var currentSeconds by mutableFloatStateOf(0f)
    var totalSeconds by mutableFloatStateOf(0f)
    var isBuffering by mutableStateOf(false)
    var currentTrackId by mutableStateOf<String?>(null)
    var onTrackEnded: (() -> Unit)? = null

    fun playTrack(track: Track, onTrackUpdated: ((Track) -> Unit)? = null) {
        val trackKey = track.videoId.ifBlank { "${track.title}_${track.artist}" }
        currentTrackId = trackKey
        currentSeconds = 0f
        isPlaying = false
        isBuffering = true

        releasePlayer()

        if (track.audioUrl.isNotBlank() && track.audioUrl.startsWith("http")) {
            startStream(track.audioUrl)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val resolvedUrl = if (track.links.isNotEmpty()) {
                    ArtistGridService.resolveTrackAudio(track.links)
                } else if (track.videoId.isNotBlank()) {
                    ArtistGridService.resolveTrackAudio(listOf(TrackLink(track.videoId)))
                } else null

                withContext(Dispatchers.Main) {
                    if (currentTrackId == trackKey) {
                        if (!resolvedUrl.isNullOrBlank()) {
                            val updated = track.copy(audioUrl = resolvedUrl)
                            onTrackUpdated?.invoke(updated)
                            startStream(resolvedUrl)
                        } else {
                            isBuffering = false
                            isPlaying = false
                        }
                    }
                }
            }
        }
    }

    private fun startStream(url: String) {
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { p ->
                    this@PlayerController.isBuffering = false
                    try {
                        p.start()
                        this@PlayerController.isPlaying = true
                        val dur = p.duration / 1000f
                        if (dur > 0f) this@PlayerController.totalSeconds = dur
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                setOnCompletionListener {
                    this@PlayerController.isPlaying = false
                    this@PlayerController.currentSeconds = this@PlayerController.totalSeconds
                    this@PlayerController.onTrackEnded?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    this@PlayerController.isBuffering = false
                    this@PlayerController.isPlaying = false
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            e.printStackTrace()
            isBuffering = false
            isPlaying = false
        }
    }

    fun play() {
        mediaPlayer?.let { mp ->
            try {
                mp.start()
                isPlaying = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.pause()
                isPlaying = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun seekTo(seconds: Float) {
        currentSeconds = seconds
        mediaPlayer?.let { mp ->
            try {
                val targetMs = (seconds * 1000).toInt().coerceIn(0, mp.duration)
                mp.seekTo(targetMs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateProgress() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    currentSeconds = mp.currentPosition / 1000f
                    val dur = mp.duration / 1000f
                    if (dur > 0f) totalSeconds = dur
                }
            } catch (_: Exception) {}
        }
    }

    fun isLoaded(): Boolean = mediaPlayer != null

    private fun releasePlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun release() {
        releasePlayer()
        isPlaying = false
        isBuffering = false
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val imageLoader = coil.ImageLoader.Builder(this)
                .components {
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        add(coil.decode.ImageDecoderDecoder.Factory())
                    } else {
                        add(coil.decode.GifDecoder.Factory())
                    }
                }
                .build()
            coil.Coil.setImageLoader(imageLoader)
        } catch (_: Exception) {}
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0C10)
                ) {
                    MusicApp()
                }
            }
        }
    }
}

@Composable
fun MusicApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) max else 15
    }
    var currentVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    val playerController = remember { PlayerController() }

    DisposableEffect(Unit) {
        onDispose {
            playerController.release()
        }
    }

    LaunchedEffect(playerController.isPlaying) {
        while (playerController.isPlaying) {
            playerController.updateProgress()
            delay(200)
        }
    }

    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var currentScreen by remember { mutableStateOf(Screen.ARTIST_GRID) }
    var previousScreen by remember { mutableStateOf(Screen.ARTIST_GRID) }

    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var isArtistsLoading by remember { mutableStateOf(true) }
    var artistSearchQuery by remember { mutableStateOf("") }

    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var trackerData by remember { mutableStateOf<TrackerData?>(null) }
    var isLoadingTracker by remember { mutableStateOf(false) }
    var selectedTabSlug by remember { mutableStateOf<String?>(null) }

    var selectedProject by remember { mutableStateOf<ProjectEra?>(null) }

    var playlist by remember { mutableStateOf(InnerTubeService.getDefaultTracks()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var currentTrack by remember { mutableStateOf<Track?>(null) }

    var favoriteTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    var isShuffle by remember { mutableStateOf(false) }
    var isRepeat by remember { mutableStateOf(false) }
    val isFavorite = favoriteTracks.any { currentTrack != null && it.title.equals(currentTrack?.title, ignoreCase = true) }

    LaunchedEffect(Unit) {
        isArtistsLoading = true
        artists = ArtistGridService.getArtists()
        isArtistsLoading = false
    }

    val filteredArtists = remember(artists, artistSearchQuery) {
        if (artistSearchQuery.isBlank()) artists else {
            val q = artistSearchQuery.trim().lowercase()
            artists.filter { it.name.lowercase().contains(q) }
        }
    }

    fun playTrackFromProject(projectTrack: ProjectTrack, artistName: String, eraName: String, eraCover: String) {
        val firstLink = projectTrack.links.firstOrNull()?.url ?: ""
        val newTrack = Track(
            title = projectTrack.title.ifBlank { projectTrack.rawName }.uppercase(),
            artist = artistName.uppercase(),
            album = eraName.uppercase(),
            duration = projectTrack.availableLength.ifBlank { "3:30" },
            videoId = firstLink,
            thumbnailUrl = eraCover.ifBlank { selectedArtist?.imageUrl ?: "" },
            audioUrl = "",
            links = projectTrack.links
        )

        playlist = listOf(newTrack)
        currentIndex = 0
        currentTrack = newTrack

        coroutineScope.launch {
            val resolvedUrl = ArtistGridService.resolveTrackAudio(projectTrack.links)
            if (!resolvedUrl.isNullOrBlank()) {
                val updated = newTrack.copy(audioUrl = resolvedUrl)
                playlist = listOf(updated)
                currentTrack = updated
                playerController.playTrack(updated)
            } else {
                playerController.isBuffering = false
                playerController.isPlaying = false
                android.widget.Toast.makeText(context, "NO PLAYABLE AUDIO STREAM IN ARTISTGRID", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun togglePlayPause() {
        if (playerController.isPlaying) {
            playerController.pause()
        } else {
            if (playerController.isLoaded()) {
                playerController.play()
            } else if (playlist.isNotEmpty()) {
                val track = playlist.getOrElse(currentIndex) { playlist.first() }
                currentTrack = track
                playerController.playTrack(track) { updated ->
                    currentTrack = updated
                }
            }
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        val nextIdx = if (isShuffle && playlist.size > 1) {
            (0 until playlist.size).filter { it != currentIndex }.randomOrNull() ?: ((currentIndex + 1) % playlist.size)
        } else {
            (currentIndex + 1) % playlist.size
        }
        currentIndex = nextIdx
        val track = playlist.getOrElse(nextIdx) { playlist.first() }
        currentTrack = track
        playerController.playTrack(track) { updated ->
            currentTrack = updated
        }
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        if (playerController.currentSeconds > 3f) {
            playerController.seekTo(0f)
        } else {
            val prevIdx = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
            currentIndex = prevIdx
            val track = playlist.getOrElse(prevIdx) { playlist.first() }
            currentTrack = track
            playerController.playTrack(track) { updated ->
                currentTrack = updated
            }
        }
    }

    playerController.onTrackEnded = {
        if (isRepeat) {
            playerController.seekTo(0f)
            playerController.play()
        } else {
            playNext()
        }
    }

    BackHandler(enabled = currentScreen != Screen.ARTIST_GRID || selectedTab != MainTab.HOME) {
        when {
            currentScreen == Screen.FULL_PLAYER -> currentScreen = previousScreen
            selectedTab != MainTab.HOME -> {
                selectedTab = MainTab.HOME
                currentScreen = Screen.ARTIST_GRID
            }
            currentScreen == Screen.PROJECT_TRACKS -> currentScreen = Screen.DISCOGRAPHY
            currentScreen == Screen.DISCOGRAPHY -> currentScreen = Screen.ARTIST_GRID
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0C10))) {
        // 1. Content Area with Bottom Inset to account for Floating Nav Dock & MiniPlayer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (currentTrack != null) 140.dp else 84.dp)
        ) {
            when (selectedTab) {
                MainTab.HOME -> {
                    when (currentScreen) {
                        Screen.ARTIST_GRID -> {
                            ArtistGridScreen(
                                artists = filteredArtists,
                                isLoading = isArtistsLoading,
                                searchQuery = artistSearchQuery,
                                onSearchQueryChange = { artistSearchQuery = it },
                                onArtistClick = { artist ->
                                    selectedArtist = artist
                                    selectedTabSlug = null
                                    selectedProject = null
                                    isLoadingTracker = true
                                    currentScreen = Screen.DISCOGRAPHY
                                    coroutineScope.launch {
                                        trackerData = ArtistGridService.getTrackerData(artist.trackerId)
                                        isLoadingTracker = false
                                    }
                                },
                                currentTrack = null
                            )
                        }
                        Screen.DISCOGRAPHY -> {
                            selectedArtist?.let { artist ->
                                DiscographyScreen(
                                    artist = artist,
                                    trackerData = trackerData,
                                    isLoading = isLoadingTracker,
                                    selectedTabSlug = selectedTabSlug,
                                    onTabSelect = { slug ->
                                        selectedTabSlug = slug
                                        isLoadingTracker = true
                                        coroutineScope.launch {
                                            trackerData = ArtistGridService.getTrackerData(artist.trackerId, slug)
                                            isLoadingTracker = false
                                        }
                                    },
                                    onBack = { currentScreen = Screen.ARTIST_GRID },
                                    onProjectClick = { project ->
                                        selectedProject = project
                                        currentScreen = Screen.PROJECT_TRACKS
                                    }
                                )
                            }
                        }
                        Screen.PROJECT_TRACKS -> {
                            if (selectedArtist != null && selectedProject != null) {
                                ProjectTracksScreen(
                                    artist = selectedArtist!!,
                                    project = selectedProject!!,
                                    currentlyPlayingTrackTitle = if (playerController.isPlaying && playlist.isNotEmpty()) currentTrack?.title else null,
                                    isFavorite = { trackTitle ->
                                        favoriteTracks.any { it.title.equals(trackTitle, ignoreCase = true) }
                                    },
                                    onToggleFavorite = { projTrack ->
                                        val title = projTrack.title.ifBlank { projTrack.rawName }.uppercase()
                                        val exists = favoriteTracks.any { it.title.equals(title, ignoreCase = true) }
                                        if (exists) {
                                            favoriteTracks = favoriteTracks.filterNot { it.title.equals(title, ignoreCase = true) }
                                        } else {
                                            val favTrack = Track(
                                                title = title,
                                                artist = selectedArtist!!.name.uppercase(),
                                                album = selectedProject!!.name.uppercase(),
                                                duration = projTrack.availableLength.ifBlank { "3:30" },
                                                videoId = projTrack.links.firstOrNull()?.url ?: "",
                                                thumbnailUrl = selectedProject!!.coverArt.ifBlank { selectedArtist!!.imageUrl },
                                                audioUrl = "",
                                                links = projTrack.links
                                            )
                                            favoriteTracks = favoriteTracks + favTrack
                                        }
                                    },
                                    onBack = { currentScreen = Screen.DISCOGRAPHY },
                                    onPlayTrack = { projTrack ->
                                        val trackTitle = projTrack.title.ifBlank { projTrack.rawName }.uppercase()
                                        val isCurrentPlaying = playerController.isPlaying &&
                                                currentTrack?.title.equals(trackTitle, ignoreCase = true)
                                        if (isCurrentPlaying) {
                                            togglePlayPause()
                                        } else {
                                            playTrackFromProject(
                                                projTrack,
                                                selectedArtist!!.name,
                                                selectedProject!!.name,
                                                selectedProject!!.coverArt
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        Screen.FULL_PLAYER -> {}
                    }
                }
                MainTab.SETTINGS -> {
                    SettingsScreen(
                        onPlayTrack = { trk ->
                            playlist = listOf(trk)
                            currentIndex = 0
                            currentTrack = trk
                            playerController.playTrack(trk) { updated ->
                                playlist = listOf(updated)
                                currentTrack = updated
                            }
                        },
                        onPlayAll = { tracks, shuffle ->
                            if (tracks.isNotEmpty()) {
                                playlist = if (shuffle) tracks.shuffled() else tracks
                                currentIndex = 0
                                currentTrack = playlist.first()
                                playerController.playTrack(playlist.first()) { updated ->
                                    currentTrack = updated
                                }
                            }
                        }
                    )
                }
                MainTab.LIBRARY -> {
                    LibraryScreen(
                        favoriteTracks = favoriteTracks,
                        currentlyPlayingTitle = if (playerController.isPlaying) currentTrack?.title else null,
                        onPlayTrack = { trk ->
                            playlist = listOf(trk)
                            currentIndex = 0
                            currentTrack = trk
                            playerController.playTrack(trk) { updated ->
                                playlist = listOf(updated)
                                currentTrack = updated
                            }
                        },
                        onPlayAll = { tracks, shuffle ->
                            if (tracks.isNotEmpty()) {
                                playlist = if (shuffle) tracks.shuffled() else tracks
                                currentIndex = 0
                                currentTrack = playlist.first()
                                playerController.playTrack(playlist.first()) { updated ->
                                    currentTrack = updated
                                }
                            }
                        },
                        onSelectArtist = { artistName ->
                            selectedTab = MainTab.HOME
                            artistSearchQuery = artistName
                            currentScreen = Screen.ARTIST_GRID
                        }
                    )
                }
            }
        }

        // 2. Docked Mini-Player + Floating Apple-style Navigation Dock (Nothing OS styled)
        if (currentScreen != Screen.FULL_PLAYER) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentTrack != null) {
                    MiniPlayerBar(
                        track = currentTrack!!,
                        isPlaying = playerController.isPlaying,
                        onPlayPauseToggle = { togglePlayPause() },
                        onClick = {
                            previousScreen = currentScreen
                            currentScreen = Screen.FULL_PLAYER
                        }
                    )
                }

                FloatingNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        selectedTab = tab
                        if (tab == MainTab.HOME) {
                            if (currentScreen != Screen.ARTIST_GRID && currentScreen != Screen.DISCOGRAPHY && currentScreen != Screen.PROJECT_TRACKS) {
                                currentScreen = Screen.ARTIST_GRID
                            }
                        }
                    }
                )
            }
        }

        // 3. Full Player Slide-Over
        AnimatedVisibility(
            visible = currentScreen == Screen.FULL_PLAYER,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            val progressFrac = if (playerController.totalSeconds > 0f) {
                (playerController.currentSeconds / playerController.totalSeconds).coerceIn(0f, 1f)
            } else 0f

            var showQueueScreen by remember { mutableStateOf(false) }

            FullPlayerScreen(
                track = currentTrack ?: Track("", ""),
                isPlaying = playerController.isPlaying,
                onPlayPauseToggle = { togglePlayPause() },
                onPrevious = { playPrevious() },
                onNext = { playNext() },
                isShuffle = isShuffle,
                onShuffleToggle = { isShuffle = !isShuffle },
                isRepeat = isRepeat,
                onRepeatToggle = { isRepeat = !isRepeat },
                isFavorite = isFavorite,
                onFavoriteToggle = {
                    if (isFavorite) {
                        favoriteTracks = favoriteTracks.filterNot { currentTrack != null && it.title.equals(currentTrack?.title, ignoreCase = true) }
                    } else {
                        currentTrack?.let { cur ->
                            if (favoriteTracks.none { it.title.equals(cur.title, ignoreCase = true) }) {
                                favoriteTracks = favoriteTracks + cur
                            }
                        }
                    }
                },
                progress = progressFrac,
                currentSeconds = playerController.currentSeconds,
                totalSeconds = playerController.totalSeconds,
                onSeek = { frac ->
                    val dur = if (playerController.totalSeconds > 0f) playerController.totalSeconds else 210f
                    playerController.seekTo(frac * dur)
                },
                audioManager = audioManager,
                maxVolume = maxVolume,
                currentVolume = currentVolume,
                onVolumeChange = { currentVolume = it },
                onMinimize = { currentScreen = previousScreen },
                onOpenQueue = { showQueueScreen = true }
            )

            if (showQueueScreen) {
                QueueScreen(
                    playlist = playlist,
                    currentIndex = currentIndex,
                    onSelectTrack = { trk, idx ->
                        currentIndex = idx
                        currentTrack = trk
                        playerController.playTrack(trk) { updated ->
                            playlist = playlist.toMutableList().apply { set(idx, updated) }
                            currentTrack = updated
                        }
                        showQueueScreen = false
                    },
                    onClose = { showQueueScreen = false }
                )
            }
        }
    }
}

@Composable
fun ArtistGridScreen(
    artists: List<Artist>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onArtistClick: (Artist) -> Unit,
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    onPlayPauseToggle: () -> Unit = {},
    onExpandPlayer: () -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedFilter by remember { mutableStateOf("ALL") }

    val displayedArtists = remember(artists, searchQuery, selectedFilter) {
        val base = if (searchQuery.isBlank()) artists else {
            val q = searchQuery.trim().lowercase()
            artists.filter { it.name.lowercase().contains(q) }
        }
        when (selectedFilter) {
            "VERIFIED" -> base.filter { it.isBest }
            "COMMUNITY" -> base.filter { !it.isBest }
            else -> base
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Brand Header (Nothing OS style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ARTISTS",
                color = Color.White,
                fontSize = 24.sp,
                fontFamily = NdotFont,
                letterSpacing = 2.sp
            )
        }

        // Spacious Monochrome Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "SEARCH ARTISTS",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 1.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.White
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = Color(0xFF888888)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color(0xFF242424),
                focusedContainerColor = Color(0xFF0E0E0E),
                unfocusedContainerColor = Color(0xFF0E0E0E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Pills Row (All, Verified, Community)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "VERIFIED", "COMMUNITY").forEach { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Color(0xFFD71921) else Color(0xFF111111))
                        .border(1.dp, if (isSelected) Color(0xFFD71921) else Color(0xFF262626), RoundedCornerShape(20.dp))
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) Color.White else Color(0xFF888888),
                        fontSize = 11.sp,
                        fontFamily = NdotFont,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Spacious Uncongested Artist List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "LOADING TRACKERS...",
                            color = Color(0xFF888888),
                            fontSize = 12.sp,
                            fontFamily = NdotFont,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            } else if (displayedArtists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "NO ARTISTS FOUND",
                        color = Color(0xFF666666),
                        fontSize = 13.sp,
                        fontFamily = NdotFont,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    items(displayedArtists, key = { it.name + it.trackerId }) { artist ->
                        ArtistGridItem(
                            artist = artist,
                            onClick = { onArtistClick(artist) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistGridItem(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular Avatar matching old favorite artist selection
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF14151B))
                .border(1.dp, Color(0xFF272A36), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (artist.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initials = artist.name.split(" ")
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .map { it.first().uppercaseChar() }
                    .joinToString("")
                Text(
                    text = initials.ifBlank { "AG" },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = NdotFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name.uppercase(),
            color = Color.White,
            fontSize = 11.5.sp,
            fontFamily = NdotFont,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.5.sp,
            modifier = Modifier.fillMaxWidth()
        )

        if (artist.isBest) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "VERIFIED",
                color = Color(0xFFD71921),
                fontSize = 8.5.sp,
                fontFamily = NdotFont,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
fun DiscographyScreen(
    artist: Artist,
    trackerData: TrackerData?,
    isLoading: Boolean,
    selectedTabSlug: String?,
    onTabSelect: (String) -> Unit,
    onBack: () -> Unit,
    onProjectClick: (ProjectEra) -> Unit,
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    onPlayPauseToggle: () -> Unit = {},
    onExpandPlayer: () -> Unit = {}
) {
    var activeTab by remember { mutableStateOf("PROJECTS") }
    var trackSearchQuery by remember { mutableStateOf("") }
    
    val allTracks = remember(trackerData) {
        trackerData?.eras?.flatMap { it.tracks.map { track -> track to it } } ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Header
        Text(
            text = artist.name.uppercase(),
            color = Color.White,
            fontSize = 24.sp,
            fontFamily = NdotFont,
            letterSpacing = 2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // TabRow: PROJECTS | TRACKS
        TabRow(
            selectedTabIndex = if (activeTab == "PROJECTS") 0 else 1,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[if (activeTab == "PROJECTS") 0 else 1]),
                    color = Color(0xFFD71921)
                )
            }
        ) {
            Tab(selected = activeTab == "PROJECTS", onClick = { activeTab = "PROJECTS" }, text = { Text("PROJECTS", fontFamily = NdotFont, fontSize = 12.sp) })
            Tab(selected = activeTab == "TRACKS", onClick = { activeTab = "TRACKS" }, text = { Text("TRACKS", fontFamily = NdotFont, fontSize = 12.sp) })
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        if (activeTab == "PROJECTS") {
            val projectSearchQuery by remember { mutableStateOf("") }
            val rawEras = trackerData?.eras ?: emptyList()
            val filteredEras = remember(rawEras, projectSearchQuery) {
                if (projectSearchQuery.isBlank()) rawEras else {
                    val q = projectSearchQuery.trim().lowercase()
                    rawEras.filter { era ->
                        era.name.lowercase().contains(q) ||
                        era.tracks.any { it.title.lowercase().contains(q) || it.rawName.lowercase().contains(q) }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
            ) {
                items(filteredEras, key = { it.name }) { era ->
                    ProjectAlbumGridItem(
                        era = era,
                        artistFallbackImage = artist.imageUrl,
                        onClick = { onProjectClick(era) }
                    )
                }
            }
        } else {
            // Tracks list logic: flat list of all tracks + search
            OutlinedTextField(
                value = trackSearchQuery,
                onValueChange = { trackSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("SEARCH TRACKS", color = Color(0xFF666666), fontSize = 12.sp, fontFamily = NdotFont) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFD71921), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allTracks.filter { it.first.title.contains(trackSearchQuery, true) }) { (track, era) ->
                    TrackDetailCard(
                        track = track,
                        artistName = artist.name,
                        albumName = era.name,
                        coverUrl = era.coverArt.ifBlank { artist.imageUrl },
                        isPlaying = false,
                        isFavorite = false,
                        onPlay = { /* ... */ },
                        onToggleFavorite = { /* ... */ },
                        onOpenLink = { url -> /* ... */ }
                    )
                }
            }
        }
    }
}

fun getHostName(track: ProjectTrack): String {
    val link = track.links.firstOrNull() ?: return ""
    val text = link.text.trim()
    if (text.isNotBlank() && !text.startsWith("http", ignoreCase = true) && text.length <= 15) {
        return text
    }
    val url = link.url.lowercase()
    return when {
        url.contains("pillow") -> "Pillows"
        url.contains("imgur") -> "Imgur"
        url.contains("kraken") -> "Kraken"
        url.contains("mega.nz") || url.contains("mega.io") -> "Mega"
        url.contains("drive.google") -> "Drive"
        url.contains("youtu") -> "YouTube"
        url.contains("soundcloud") -> "SoundCloud"
        url.contains("filebin") -> "Filebin"
        url.contains("catbox") -> "Catbox"
        url.contains("streamable") -> "Streamable"
        url.contains("onlyfiles") -> "OnlyFiles"
        url.contains("wi.to") -> "Wi.to"
        url.contains("dbree") -> "Dbree"
        url.contains("dropbox") -> "Dropbox"
        url.contains("archive.org") -> "Archive"
        else -> {
            try {
                val host = Uri.parse(link.url).host ?: ""
                host.removePrefix("www.").split(".").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Source"
            } catch (e: Exception) {
                "Source"
            }
        }
    }
}

@Composable
fun ProjectAlbumGridItem(
    era: ProjectEra,
    artistFallbackImage: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val songsCount = era.tracks.size
    val playableCount = era.tracks.count { it.links.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(era) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showMenu = true }
                )
            }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1:1 round album artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF14151B))
                .border(1.dp, Color(0xFF262834), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            val artUrl = era.coverArt.ifBlank { artistFallbackImage }
            if (artUrl.isNotBlank()) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = era.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = era.name.take(2).uppercase(),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = NdotFont,
                    fontWeight = FontWeight.Bold
                )
            }

            // Dropdown menu showing options on press-and-hold
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .widthIn(min = 180.dp)
                    .background(Color(0xFF121318))
                    .border(1.dp, Color(0xFF2C2F3C), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "VIEW TRACKS",
                            fontFamily = NdotFont,
                            color = Color.White,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "ALBUM INFO",
                            fontFamily = NdotFont,
                            color = Color.White,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    },
                    onClick = {
                        showMenu = false
                        val desc = era.timeline.ifBlank { era.description }.ifBlank { "No additional info" }
                        android.widget.Toast.makeText(
                            context,
                            "${era.name}: $songsCount tracks ($playableCount playable)\n$desc",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title under the album
        Text(
            text = era.name.uppercase(),
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = NdotFont,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.5.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun ProjectTracksScreen(
    artist: Artist,
    project: ProjectEra,
    currentlyPlayingTrackTitle: String?,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (ProjectTrack) -> Unit,
    onBack: () -> Unit,
    onPlayTrack: (ProjectTrack) -> Unit,
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    onPlayPauseToggle: () -> Unit = {},
    onExpandPlayer: () -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var trackSearchQuery by remember { mutableStateOf("") }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    val filteredTracks = remember(project.tracks, trackSearchQuery) {
        if (trackSearchQuery.isBlank()) project.tracks else {
            val q = trackSearchQuery.trim().lowercase()
            project.tracks.filter {
                it.title.lowercase().contains(q) ||
                it.rawName.lowercase().contains(q) ||
                it.credits.any { c -> c.lowercase().contains(q) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Top Header
        Text(text = artist.name.uppercase(), color = Color.White, fontSize = 20.sp, fontFamily = NdotFont, letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = trackSearchQuery,
            onValueChange = { trackSearchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("SEARCH TRACKS", color = Color(0xFF666666), fontSize = 12.sp, fontFamily = NdotFont) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFD71921), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filteredTracks) { track ->
                TrackDetailCard(
                    track = track,
                    artistName = artist.name,
                    albumName = project.name,
                    coverUrl = project.coverArt.ifBlank { artist.imageUrl },
                    isPlaying = currentlyPlayingTrackTitle.equals(track.title.ifBlank { track.rawName }, true),
                    isFavorite = isFavorite(track.title.ifBlank { track.rawName }),
                    onPlay = { onPlayTrack(track) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onOpenLink = { url -> /* ... */ }
                )
            }
        }
    }
}

@Composable
fun TrackDetailCard(
    track: ProjectTrack,
    artistName: String = "",
    albumName: String = "",
    coverUrl: String = "",
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showHoldMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    val hasAudio = track.links.isNotEmpty()

    val asTrack = remember(track, artistName, albumName, coverUrl) {
        Track(
            title = track.title.ifBlank { track.rawName }.uppercase(),
            artist = artistName.uppercase(),
            album = albumName.uppercase(),
            duration = track.availableLength.ifBlank { "3:30" },
            videoId = track.links.firstOrNull()?.url ?: "",
            thumbnailUrl = coverUrl,
            audioUrl = "",
            links = track.links
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isPlaying) Color(0xFF161822) else Color(0xFF0E0F14))
                .pointerInput(track) {
                    detectTapGestures(
                        onTap = { onPlay() },
                        onLongPress = { showHoldMenu = true }
                    )
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Project cover on the left
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181920)),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ArtistGridService.toHqCoverUrl(coverUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD71921))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.ifBlank { track.rawName }.uppercase(),
                    color = if (isPlaying) Color(0xFFD71921) else Color.White,
                    fontSize = 13.5.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val creditsText = if (track.credits.isNotEmpty()) {
                    track.credits.joinToString(" ")
                } else if (track.rawName.isNotBlank() && track.rawName != track.title) {
                    track.rawName.removePrefix(track.title).trim()
                } else ""

                if (creditsText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = creditsText.uppercase(),
                        color = Color(0xFF7E838E),
                        fontSize = 10.5.sp,
                        fontFamily = NdotFont,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (track.availableLength.isNotBlank()) {
                Text(
                    text = track.availableLength,
                    color = Color(0xFF7E838E),
                    fontSize = 11.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Dropdown menu showing options on hold
        DropdownMenu(
            expanded = showHoldMenu,
            onDismissRequest = { showHoldMenu = false },
            modifier = Modifier
                .widthIn(min = 230.dp)
                .background(Color(0xFF121318))
                .border(1.dp, Color(0xFF282B38), RoundedCornerShape(14.dp))
                .padding(6.dp)
        ) {
            // 1. FAVORITE
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isFavorite) "UNFAVORITE" else "FAVORITE",
                        fontFamily = NdotFont,
                        color = Color.White,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFD71921) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showHoldMenu = false
                    onToggleFavorite()
                }
            )

            // 2. ADD TO PLAYLIST
            DropdownMenuItem(
                text = {
                    Text(
                        text = "ADD TO PLAYLIST",
                        fontFamily = NdotFont,
                        color = Color.White,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showHoldMenu = false
                    playlists = LocalStorageManager.getPlaylists(context)
                    showAddToPlaylistDialog = true
                }
            )

            // 3. DOWNLOAD
            DropdownMenuItem(
                text = {
                    Text(
                        text = "DOWNLOAD",
                        fontFamily = NdotFont,
                        color = Color.White,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showHoldMenu = false
                    Toast.makeText(context, "DOWNLOADING...", Toast.LENGTH_SHORT).show()
                    coroutineScope.launch {
                        val success = LocalStorageManager.downloadTrackFile(context, asTrack)
                        if (success) {
                            Toast.makeText(context, "DOWNLOADED", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "DOWNLOAD FAILED", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            // 4. OPEN LINK
            if (hasAudio) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "OPEN LINK",
                            fontFamily = NdotFont,
                            color = Color.White,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showHoldMenu = false
                        val firstLink = track.links.firstOrNull()?.url ?: ""
                        if (firstLink.isNotBlank()) onOpenLink(firstLink)
                    }
                )
            }

            // 5. TRACK INFO
            DropdownMenuItem(
                text = {
                    Text(
                        text = "TRACK INFO",
                        fontFamily = NdotFont,
                        color = Color.White,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showHoldMenu = false
                    val noteMsg = track.notes.ifBlank { "No notes available for this track." }
                    android.widget.Toast.makeText(
                        context,
                        "${track.title}\n$noteMsg",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = Color(0xFF242735)
            )

            // Bottom section: Source, Quality, and Snippet vs Full, and Is Best Only
            // Fine red text without borders like Nothing OS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val host = getHostName(track).ifBlank { "DIRECT" }
                val quality = track.quality.ifBlank { "STANDARD" }
                val isSnippet = track.rawName.contains("snippet", ignoreCase = true) ||
                        track.notes.contains("snippet", ignoreCase = true) ||
                        track.quality.contains("snippet", ignoreCase = true)
                val typeLabel = if (isSnippet) "SNIPPET" else "FULL TRACK"

                val isBest = track.notes.contains("best", ignoreCase = true) ||
                        track.rawName.contains("best", ignoreCase = true) ||
                        track.quality.contains("best", ignoreCase = true) ||
                        track.quality.contains("lossless", ignoreCase = true)

                Text(
                    text = "SOURCE: ${host.uppercase()}",
                    color = Color(0xFFD71921),
                    fontSize = 10.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "QUALITY: ${quality.uppercase()}",
                    color = Color(0xFFD71921),
                    fontSize = 10.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "TYPE: $typeLabel",
                    color = Color(0xFFD71921),
                    fontSize = 10.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "BEST ONLY: ${if (isBest) "YES" else "NO"}",
                    color = Color(0xFFD71921),
                    fontSize = 10.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 0.8.sp
                )
            }
        }

        // Add to Playlist Dialog
        if (showAddToPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showAddToPlaylistDialog = false },
                containerColor = Color(0xFF14151C),
                title = {
                    Text("ADD TO PLAYLIST", color = Color.White, fontFamily = NdotFont, fontSize = 16.sp)
                },
                text = {
                    if (playlists.isEmpty()) {
                        Text("NO PLAYLISTS YET. CREATE ONE IN LIBRARY.", color = Color(0xFF7E838E), fontFamily = NdotFont, fontSize = 12.sp)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(playlists) { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E202B))
                                        .clickable {
                                            LocalStorageManager.addTrackToPlaylist(context, p.id, asTrack)
                                            showAddToPlaylistDialog = false
                                            Toast.makeText(context, "ADDED TO ${p.title.uppercase()}", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(p.title.uppercase(), color = Color.White, fontFamily = NdotFont, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddToPlaylistDialog = false }) {
                        Text("CLOSE", color = Color(0xFFD71921), fontFamily = NdotFont)
                    }
                }
            )
        }
    }
}

@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F0F0F))
            .border(1.dp, Color(0xFF282828), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF181818))
                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(10.dp))
            ) {
                if (track.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.uppercase(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = NdotFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist.uppercase(),
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontFamily = NdotFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onPlayPauseToggle,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C1C1C))
                .border(1.dp, Color(0xFF333333), CircleShape)
        ) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (isPlaying) {
                        drawRect(color = Color.White, topLeft = Offset(4f, 2f), size = Size(4f, size.height - 4f))
                        drawRect(color = Color.White, topLeft = Offset(size.width - 8f, 2f), size = Size(4f, size.height - 4f))
                    } else {
                        val path = Path().apply {
                            moveTo(5f, 2f)
                            lineTo(size.width - 2f, size.height / 2f)
                            lineTo(5f, size.height - 2f)
                            close()
                        }
                        drawPath(path, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun FullPlayerScreen(
    track: Track,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    isShuffle: Boolean,
    onShuffleToggle: () -> Unit,
    isRepeat: Boolean,
    onRepeatToggle: () -> Unit,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    progress: Float,
    currentSeconds: Float,
    totalSeconds: Float,
    onSeek: (Float) -> Unit,
    audioManager: AudioManager,
    maxVolume: Int,
    currentVolume: Int,
    onVolumeChange: (Int) -> Unit,
    onMinimize: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isVolumeMode by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Minimize Chevron + Album Header (Real Album Name)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMinimize, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = if (track.album.isNotBlank()) track.album else track.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)
                )

                // Invisible placeholder to keep header centered
                Spacer(modifier = Modifier.size(44.dp))
            }

            // HQ Album Art Container
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141720))
            ) {
                if (track.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val artResId = remember(context) {
                        context.resources.getIdentifier("album_art", "drawable", context.packageName)
                    }
                    if (artResId != 0) {
                        Image(
                            painter = painterResource(id = artResId),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Track & Artist Info + Solid Filled Favorite Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = NdotFont,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        color = Color(0xFF8E939E),
                        fontSize = 14.sp,
                        fontFamily = NdotFont,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                }

                var showPlayerMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showPlayerMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showPlayerMenu,
                        onDismissRequest = { showPlayerMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF121318))
                            .border(1.dp, Color(0xFF282B38), RoundedCornerShape(14.dp))
                            .padding(4.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isFavorite) "UNFAVORITE" else "FAVORITE", fontFamily = NdotFont, color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                showPlayerMenu = false
                                onFavoriteToggle()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("ADD TO PLAYLIST", fontFamily = NdotFont, color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                showPlayerMenu = false
                                Toast.makeText(context, "ADDED", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("DOWNLOAD", fontFamily = NdotFont, color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                showPlayerMenu = false
                                Toast.makeText(context, "DOWNLOADING...", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    LocalStorageManager.downloadTrackFile(context, track)
                                    Toast.makeText(context, "DOWNLOADED", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Progress Bar & Real Time Text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .pointerInput(totalSeconds) {
                            detectTapGestures { offset ->
                                if (size.width > 0 && totalSeconds > 0f) {
                                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                    onSeek(frac)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp)),
                        color = Color.White,
                        trackColor = Color(0xFF1F232F),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val curSecInt = currentSeconds.toInt()
                    val curStr = String.format("%02d:%02d", curSecInt / 60, curSecInt % 60)

                    val durStr = if (totalSeconds > 0f) {
                        val totalSecInt = totalSeconds.toInt()
                        String.format("%02d:%02d", totalSecInt / 60, totalSecInt % 60)
                    } else {
                        track.duration
                    }

                    Text(
                        text = curStr,
                        color = Color(0xFF8E939E),
                        fontSize = 15.sp,
                        fontFamily = NdotFont
                    )
                    Text(
                        text = durStr,
                        color = Color(0xFF8E939E),
                        fontSize = 15.sp,
                        fontFamily = NdotFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls Row: Previous | CMF Dial with 0.5s Hold Volume | Next
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Track (Solid Filled)
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            drawRect(color = Color.White, topLeft = Offset(0f, 0f), size = Size(4.dp.toPx(), h))
                            val path = Path().apply {
                                moveTo(w, 2.dp.toPx())
                                lineTo(w * 0.3f, h * 0.5f)
                                lineTo(w, h - 2.dp.toPx())
                                close()
                            }
                            drawPath(path = path, color = Color.White)
                        }
                    }
                }

                // CMF Dial Play Button with 0.5s Hold Detection
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown()
                                    val initialPosition = down.position
                                    val slop = viewConfiguration.touchSlop
                                    val timeout = 500L
                                    val startTime = System.currentTimeMillis()
                                    var enteredVolumeMode = false

                                    loop@ while (true) {
                                        val elapsed = System.currentTimeMillis() - startTime
                                        val remaining = timeout - elapsed
                                        if (remaining <= 0) {
                                            enteredVolumeMode = true
                                            isVolumeMode = true
                                            onVolumeChange(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
                                            break@loop
                                        }

                                        val event = withTimeoutOrNull(remaining) {
                                            awaitPointerEvent()
                                        } ?: run {
                                            enteredVolumeMode = true
                                            isVolumeMode = true
                                            onVolumeChange(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
                                            break@loop
                                        }

                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) {
                                            break@loop
                                        }
                                        val distance = (change.position - initialPosition).getDistance()
                                        if (distance > slop) {
                                            enteredVolumeMode = true
                                            isVolumeMode = true
                                            onVolumeChange(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
                                            break@loop
                                        }
                                    }

                                    if (enteredVolumeMode) {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: break
                                            if (!change.pressed) break
                                            val dy = initialPosition.y - change.position.y
                                            val volDelta = (dy / 14f).toInt()
                                            val targetVol = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + volDelta).coerceIn(0, maxVolume)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                            onVolumeChange(targetVol)
                                        }
                                        isVolumeMode = false
                                    } else {
                                        onPlayPauseToggle()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerSocketRadius = size.minDimension / 2f - 1.dp.toPx()
                        val dialRadius = outerSocketRadius - 4.dp.toPx()

                        // 1. Recessed Bezel Socket
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF161A24), Color(0xFF0A0C12)),
                                center = center,
                                radius = outerSocketRadius
                            ),
                            radius = outerSocketRadius,
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFF050608),
                            radius = outerSocketRadius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // 2. Dial Disc Surface
                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF242A38), Color(0xFF181B24), Color(0xFF101218)),
                                start = Offset(center.x - dialRadius, center.y - dialRadius),
                                end = Offset(center.x + dialRadius, center.y + dialRadius)
                            ),
                            radius = dialRadius,
                            center = center
                        )

                        // 3. Outer Bevel Rim
                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF32394A), Color(0xFF1E232E), Color(0xFF0E1015)),
                                start = Offset(center.x - dialRadius, center.y - dialRadius),
                                end = Offset(center.x + dialRadius, center.y + dialRadius)
                            ),
                            radius = dialRadius,
                            center = center,
                            style = Stroke(width = 1.8.dp.toPx())
                        )

                        fun drawColorEngravedSegment(start: Offset, end: Offset, width: Float, color: Color) {
                            drawLine(
                                color = color,
                                start = Offset(start.x, start.y + 1.dp.toPx()),
                                end = Offset(end.x, end.y + 1.dp.toPx()),
                                strokeWidth = width,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = color,
                                start = start,
                                end = end,
                                strokeWidth = width * 0.85f,
                                cap = StrokeCap.Round
                            )
                        }

                        // 4. Dial Dashes: Red and White (two parallel vertical lines representing volume)
                        val slitWidth = 3.0.dp.toPx()
                        val dashLength = dialRadius * 0.45f
                        val redFraction = if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f
                        
                        // Current angle based on volume (180 range)
                        val angleDeg = -90.0 + (redFraction * 180.0)
                        val rad = Math.toRadians(angleDeg)
                        
                        // Center of the lines offset by some radius
                        val r = dialRadius * 0.4f
                        val lineOffset = 6.dp.toPx()
                        
                        // Line 1 (Red - Volume)
                        val redLineStart = Offset((center.x + r * cos(rad) - lineOffset * sin(rad)).toFloat(), (center.y + r * sin(rad) + lineOffset * cos(rad)).toFloat())
                        val redLineEnd = Offset((center.x + (r + dashLength) * cos(rad) - lineOffset * sin(rad)).toFloat(), (center.y + (r + dashLength) * sin(rad) + lineOffset * cos(rad)).toFloat())
                        
                        // Line 2 (White - Parallel)
                        val whiteLineStart = Offset((center.x + r * cos(rad) + lineOffset * sin(rad)).toFloat(), (center.y + r * sin(rad) - lineOffset * cos(rad)).toFloat())
                        val whiteLineEnd = Offset((center.x + (r + dashLength) * cos(rad) + lineOffset * sin(rad)).toFloat(), (center.y + (r + dashLength) * sin(rad) - lineOffset * cos(rad)).toFloat())
                        
                        drawLine(color = Color(0xFFD71921), start = redLineStart, end = redLineEnd, strokeWidth = slitWidth, cap = StrokeCap.Round)
                        drawLine(color = Color.White, start = whiteLineStart, end = whiteLineEnd, strokeWidth = slitWidth, cap = StrokeCap.Round)

                        // 5. Solid Filled Play / Pause Icon (Hidden when in Volume Mode)
                        if (!isVolumeMode) {
                            if (isPlaying) {
                                val barW = 4.dp.toPx()
                                val barH = 16.dp.toPx()
                                val gap = 5.5.dp.toPx()

                                val leftBarX = center.x - gap / 2f - barW
                                val rightBarX = center.x + gap / 2f

                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(leftBarX, center.y - barH / 2f),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                                )
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(rightBarX, center.y - barH / 2f),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                                )
                            } else {
                                val triH = 18.dp.toPx()
                                val triW = 16.dp.toPx()
                                val xShift = 1.5.dp.toPx()

                                val p1 = Offset(center.x - triW / 2f + xShift, center.y - triH / 2f)
                                val p2 = Offset(center.x + triW / 2f + xShift, center.y)
                                val p3 = Offset(center.x - triW / 2f + xShift, center.y + triH / 2f)

                                val fillPath = Path().apply {
                                    moveTo(p1.x, p1.y)
                                    lineTo(p2.x, p2.y)
                                    lineTo(p3.x, p3.y)
                                    close()
                                }
                                drawPath(path = fillPath, color = Color.White)
                            }
                        }
                    }

                    // Centered Volume Percentage when in Volume Mode
                    if (isVolumeMode) {
                        val pct = if (maxVolume > 0) (currentVolume.toFloat() / maxVolume * 100).toInt() else 0
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x4D0A0C10)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$pct%",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontFamily = NdotFont,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Next Track (Solid Filled)
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val path = Path().apply {
                                moveTo(0f, 2.dp.toPx())
                                lineTo(w * 0.7f, h * 0.5f)
                                lineTo(0f, h - 2.dp.toPx())
                                close()
                            }
                            drawPath(path = path, color = Color.White)
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(w - 4.dp.toPx(), 0f),
                                size = Size(4.dp.toPx(), h)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Secondary Controls: Shuffle | Queue | Repeat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Remade Shuffle Icon (Two crossing paths with sharp filled arrowheads)
                IconButton(
                    onClick = onShuffleToggle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val color = if (isShuffle) Color.White else Color(0xFF6C717C)
                            val w = size.width
                            val h = size.height

                            // Track 1: Top-left to bottom-right
                            val p1 = Path().apply {
                                moveTo(w * 0.15f, h * 0.3f)
                                cubicTo(w * 0.45f, h * 0.3f, w * 0.55f, h * 0.7f, w * 0.72f, h * 0.7f)
                            }
                            drawPath(p1, color = color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))

                            // Arrowhead 1 (Pointing right at bottom)
                            val arrow1 = Path().apply {
                                moveTo(w * 0.65f, h * 0.58f)
                                lineTo(w * 0.88f, h * 0.7f)
                                lineTo(w * 0.65f, h * 0.82f)
                                close()
                            }
                            drawPath(arrow1, color = color)

                            // Track 2: Bottom-left to top-right
                            val p2 = Path().apply {
                                moveTo(w * 0.15f, h * 0.7f)
                                cubicTo(w * 0.45f, h * 0.7f, w * 0.55f, h * 0.3f, w * 0.72f, h * 0.3f)
                            }
                            drawPath(p2, color = color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))

                            // Arrowhead 2 (Pointing right at top)
                            val arrow2 = Path().apply {
                                moveTo(w * 0.65f, h * 0.18f)
                                lineTo(w * 0.88f, h * 0.3f)
                                lineTo(w * 0.65f, h * 0.42f)
                                close()
                            }
                            drawPath(arrow2, color = color)
                        }
                    }
                }

                // Queue Button
                IconButton(onClick = onOpenQueue, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Remade Repeat Icon (Clean rounded rectangular loop with two opposing directional arrows)
                IconButton(
                    onClick = onRepeatToggle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val color = if (isRepeat) Color.White else Color(0xFF6C717C)
                            val w = size.width
                            val h = size.height

                            // Top track going right
                            val pTop = Path().apply {
                                moveTo(w * 0.25f, h * 0.65f)
                                lineTo(w * 0.25f, h * 0.35f)
                                cubicTo(w * 0.25f, h * 0.32f, w * 0.28f, h * 0.32f, w * 0.32f, h * 0.32f)
                                lineTo(w * 0.72f, h * 0.32f)
                            }
                            drawPath(pTop, color = color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))

                            // Top arrow pointing right
                            val arrowTop = Path().apply {
                                moveTo(w * 0.65f, h * 0.20f)
                                lineTo(w * 0.88f, h * 0.32f)
                                lineTo(w * 0.65f, h * 0.44f)
                                close()
                            }
                            drawPath(arrowTop, color = color)

                            // Bottom track going left
                            val pBottom = Path().apply {
                                moveTo(w * 0.75f, h * 0.35f)
                                lineTo(w * 0.75f, h * 0.68f)
                                cubicTo(w * 0.75f, h * 0.71f, w * 0.72f, h * 0.71f, w * 0.68f, h * 0.71f)
                                lineTo(w * 0.28f, h * 0.71f)
                            }
                            drawPath(pBottom, color = color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))

                            // Bottom arrow pointing left
                            val arrowBottom = Path().apply {
                                moveTo(w * 0.35f, h * 0.59f)
                                lineTo(w * 0.12f, h * 0.71f)
                                lineTo(w * 0.35f, h * 0.83f)
                                close()
                            }
                            drawPath(arrowBottom, color = color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QueueScreen(
    playlist: List<Track>,
    currentIndex: Int,
    onSelectTrack: (Track, Int) -> Unit,
    onClose: () -> Unit
) {
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragOffsetY = 0f },
                    onDragEnd = {
                        if (dragOffsetY > 80f) {
                            onClose()
                        }
                        dragOffsetY = 0f
                    },
                    onVerticalDrag = { _, dragAmount: Float ->
                        dragOffsetY += dragAmount
                    }
                )
            }
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUEUE",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 2.sp
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                itemsIndexed(playlist) { index, track ->
                    val isCurrent = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) Color(0xFF161822) else Color(0xFF0E0F14))
                            .clickable { onSelectTrack(track, index) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF181920)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (track.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ArtistGridService.toHqCoverUrl(track.thumbnailUrl),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title.uppercase(),
                                color = if (isCurrent) Color(0xFFD71921) else Color.White,
                                fontSize = 13.5.sp,
                                fontFamily = NdotFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist.uppercase(),
                                color = Color(0xFF7E838E),
                                fontSize = 10.5.sp,
                                fontFamily = NdotFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = track.duration,
                            color = Color(0xFF7E838E),
                            fontSize = 11.sp,
                            fontFamily = NdotFont
                        )
                    }
                }
            }
        }
    }
}
