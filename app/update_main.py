with open('/app/src/main/java/com/example/MainActivity.kt') as f:
    content = f.read()

replacement = '''@Composable
fun ArtistGridScreen(
    artists: List<Artist>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onArtistClick: (Artist) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onExpandPlayer: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Brand Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ARTISTGRID",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE5161A))
                )
            }

            if (!isLoading) {
                Text(
                    text = "${artists.size} ARTISTS",
                    color = Color(0xFF6E737E),
                    fontSize = 12.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 1.sp
                )
            }
        }

        // Search Bar (Under brand header)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "SEARCH ARTISTS...",
                        color = Color(0xFF6E737E),
                        fontSize = 13.sp,
                        fontFamily = NdotFont
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFFE5161A)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFF8E939E)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE5161A),
                    unfocusedBorderColor = Color(0xFF232734),
                    focusedContainerColor = Color(0xFF131620),
                    unfocusedContainerColor = Color(0xFF131620),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3 in Each Row Artist Grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFFE5161A),
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "FETCHING ARTISTGRID...",
                            color = Color(0xFF8E939E),
                            fontSize = 13.sp,
                            fontFamily = NdotFont,
                            letterSpacing = 1.sp
                        )
                    }
                }
            } else if (artists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "NO ARTISTS FOUND",
                        color = Color(0xFF6E737E),
                        fontSize = 14.sp,
                        fontFamily = NdotFont
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                ) {
                    items(artists, key = { it.name + it.trackerId }) { artist ->
                        ArtistGridItem(
                            artist = artist,
                            onClick = { onArtistClick(artist) }
                        )
                    }
                }
            }
        }

        // Mini player docked at bottom
        if (currentTrack != null) {
            MiniPlayerBar(
                track = currentTrack,
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onClick = onExpandPlayer
            )
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
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF12151E))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color(0xFF1B202D))
                .border(1.2.dp, Color(0xFF262C3D), CircleShape),
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

            if (artist.isBest) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE5161A))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name.uppercase(),
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = NdotFont,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun ProjectsListScreen(
    artist: Artist,
    trackerData: TrackerData?,
    isLoading: Boolean,
    selectedTabSlug: String?,
    onTabSelect: (String) -> Unit,
    onBack: () -> Unit,
    onProjectClick: (ProjectEra) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onExpandPlayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name.uppercase(),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = NdotFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "PROJECTS & ERAS",
                    color = Color(0xFFE5161A),
                    fontSize = 11.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 1.sp
                )
            }
        }

        // Tabs Row if available
        if (trackerData != null && trackerData.tabs.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = trackerData.tabs.indexOfFirst { it.slug == (selectedTabSlug ?: trackerData.currentTab) }.coerceAtLeast(0),
                containerColor = Color(0xFF0A0C10),
                contentColor = Color(0xFFE5161A),
                edgePadding = 16.dp,
                divider = {}
            ) {
                trackerData.tabs.forEach { tab ->
                    val isSelected = tab.slug == (selectedTabSlug ?: trackerData.currentTab)
                    Tab(
                        selected = isSelected,
                        onClick = { onTabSelect(tab.slug) },
                        text = {
                            Text(
                                text = tab.name.uppercase(),
                                fontFamily = NdotFont,
                                fontSize = 12.sp,
                                color = if (isSelected) Color(0xFFE5161A) else Color(0xFF8E939E)
                            )
                        }
                    )
                }
            }
        }

        // Projects List Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFFE5161A),
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "LOADING PROJECTS...",
                            color = Color(0xFF8E939E),
                            fontSize = 13.sp,
                            fontFamily = NdotFont,
                            letterSpacing = 1.sp
                        )
                    }
                }
            } else if (trackerData == null || trackerData.eras.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "NO PROJECTS AVAILABLE FOR THIS TRACKER",
                        color = Color(0xFF6E737E),
                        fontSize = 13.sp,
                        fontFamily = NdotFont,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)
                ) {
                    items(trackerData.eras) { era ->
                        ProjectEraCard(
                            era = era,
                            artistFallbackImage = artist.imageUrl,
                            onClick = { onProjectClick(era) }
                        )
                    }
                }
            }
        }

        // Docked Mini Player
        if (currentTrack != null) {
            MiniPlayerBar(
                track = currentTrack,
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onClick = onExpandPlayer
            )
        }
    }
}

@Composable
fun ProjectEraCard(
    era: ProjectEra,
    artistFallbackImage: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF131722))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E2332)),
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
                    fontSize = 18.sp,
                    fontFamily = NdotFont
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = era.name.uppercase(),
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = NdotFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (era.timeline.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = era.timeline.replace(Regex("""[\r\n]+"""), " • ").take(40),
                    color = Color(0xFFE5161A),
                    fontSize = 11.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )
            }

            if (era.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = era.description.replace(Regex("""[\r\n]+"""), " "),
                    color = Color(0xFF8E939E),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${era.tracks.size} TRACKS",
                color = Color(0xFF6E737E),
                fontSize = 10.sp,
                fontFamily = NdotFont,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun ProjectTracksScreen(
    artist: Artist,
    project: ProjectEra,
    currentlyPlayingTrackTitle: String?,
    onBack: () -> Unit,
    onPlayTrack: (ProjectTrack) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onExpandPlayer: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name.uppercase(),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = NdotFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.name.uppercase()} • ${project.tracks.size} TRACKS",
                    color = Color(0xFF8E939E),
                    fontSize = 11.sp,
                    fontFamily = NdotFont
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Project Header Banner
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF131722))
                        .padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E2332))
                    ) {
                        val art = project.coverArt.ifBlank { artist.imageUrl }
                        if (art.isNotBlank()) {
                            AsyncImage(
                                model = art,
                                contentDescription = project.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = project.name.uppercase(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontFamily = NdotFont
                        )
                        if (project.timeline.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = project.timeline.replace(Regex("""[\r\n]+"""), " "),
                                color = Color(0xFFE5161A),
                                fontSize = 11.sp,
                                fontFamily = NdotFont
                            )
                        }
                        if (project.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = project.description,
                                color = Color(0xFF8E939E),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Section Header
            item {
                Text(
                    text = "TRACKS & RECORDINGS",
                    color = Color(0xFF6E737E),
                    fontSize = 12.sp,
                    fontFamily = NdotFont,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            // Tracks
            itemsIndexed(project.tracks) { index, track ->
                val isCurrentPlaying = currentlyPlayingTrackTitle != null &&
                        currentlyPlayingTrackTitle.equals(track.title, ignoreCase = true)

                TrackDetailCard(
                    index = index + 1,
                    track = track,
                    isPlaying = isCurrentPlaying,
                    onPlay = { onPlayTrack(track) },
                    onOpenLink = { url ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }
        }

        // Docked Mini Player
        if (currentTrack != null) {
            MiniPlayerBar(
                track = currentTrack,
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onClick = onExpandPlayer
            )
        }
    }
}

@Composable
fun TrackDetailCard(
    index: Int,
    track: ProjectTrack,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPlaying) Color(0xFF1B202D) else Color(0xFF12151D))
            .border(
                1.dp,
                if (isPlaying) Color(0xFFE5161A) else Color(0xFF1A1F2C),
                RoundedCornerShape(14.dp)
            )
            .clickable { onPlay() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("%02d", index),
                    color = if (isPlaying) Color(0xFFE5161A) else Color(0xFF6E737E),
                    fontSize = 12.sp,
                    fontFamily = NdotFont
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title.uppercase(),
                        color = if (isPlaying) Color(0xFFE5161A) else Color.White,
                        fontSize = 15.sp,
                        fontFamily = NdotFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (track.credits.isNotEmpty()) {
                        Text(
                            text = track.credits.joinToString(" • ").uppercase(),
                            color = Color(0xFF8E939E),
                            fontSize = 11.sp,
                            fontFamily = NdotFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) Color(0xFFE5161A) else Color(0xFF1F2432))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        val hasBadges = track.quality.isNotBlank() || track.availableLength.isNotBlank() || track.leakDate.isNotBlank() || track.fileDate.isNotBlank()
        if (hasBadges) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (track.quality.isNotBlank()) {
                    QualityPill(text = track.quality.uppercase(), color = Color(0xFF3B82F6))
                }
                if (track.availableLength.isNotBlank()) {
                    QualityPill(text = track.availableLength.uppercase(), color = Color(0xFF10B981))
                }
                if (track.leakDate.isNotBlank()) {
                    QualityPill(text = "LEAK: ${track.leakDate.uppercase()}", color = Color(0xFFE5161A))
                } else if (track.fileDate.isNotBlank()) {
                    QualityPill(text = "DATE: ${track.fileDate.uppercase()}", color = Color(0xFF8B5CF6))
                }
            }
        }

        if (track.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = track.notes.replace(Regex("""[\r\n]+"""), " "),
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        if (track.links.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                track.links.take(2).forEach { link ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C212E))
                            .clickable { onOpenLink(link.url) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (link.text.ifBlank { "TRACK SOURCE" }).uppercase(),
                            color = Color(0xFF60A5FA),
                            fontSize = 10.sp,
                            fontFamily = NdotFont
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open Link",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QualityPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.8.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontFamily = NdotFont,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF181C26))
            .border(1.dp, Color(0xFF262C3D), RoundedCornerShape(16.dp))
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222634))
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
                    text = track.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = NdotFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color(0xFF8E939E),
                    fontSize = 11.sp,
                    fontFamily = NdotFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onPlayPauseToggle,
            modifier = Modifier.size(38.dp)
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (isPlaying) {
                        drawRect(color = Color.White, topLeft = Offset(4f, 2f), size = Size(5f, size.height - 4f))
                        drawRect(color = Color.White, topLeft = Offset(size.width - 9f, 2f), size = Size(5f, size.height - 4f))
                    } else {
                        val path = Path().apply {
                            moveTo(4f, 2f)
                            lineTo(size.width - 2f, size.height / 2f)
                            lineTo(4f, size.height - 2f)
                            close()
                        }
                        drawPath(path, color = Color.White)
                    }
                }
            }
        }
    }
}'''

start_str = '@Composable\nfun HomeScreen('
end_str = '@Composable\nfun FullPlayerScreen('

idx1 = content.find(start_str)
idx2 = content.find(end_str)

if idx1 == -1 or idx2 == -1:
    print(f'ERROR: idx1={idx1}, idx2={idx2}')
else:
    new_content = content[:idx1] + replacement + '\n\n' + content[idx2:]
    with open('/app/src/main/java/com/example/MainActivity.kt', 'w') as f:
        f.write(new_content)
    print('SUCCESS')
