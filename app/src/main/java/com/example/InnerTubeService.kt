package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object InnerTubeService {

    private const val DEEZER_SEARCH_URL = "https://api.deezer.com/search"
    private const val YTM_SEARCH_URL = "https://music.youtube.com/youtubei/v1/search"
    private val DURATION_REGEX = Regex("""^\d+:\d+(:\d+)?$""")

    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext getDefaultTracks()

        // 1. Try Primary high-speed direct audio provider (Deezer API)
        val directTracks = searchDeezer(trimmed)
        if (directTracks.isNotEmpty()) {
            return@withContext directTracks
        }

        // 2. Fallback to YouTube Music InnerTube API
        val ytmTracks = searchInnerTube(trimmed)
        if (ytmTracks.isNotEmpty()) {
            return@withContext ytmTracks
        }

        // 3. Fallback to filtered default tracks
        getDefaultTracks(trimmed)
    }

    private fun searchDeezer(query: String): List<Track> {
        val tracks = mutableListOf<Track>()
        var conn: HttpURLConnection? = null
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$DEEZER_SEARCH_URL?q=$encodedQuery&limit=30")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val root = JSONObject(responseStr)
                val data = root.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val title = item.optString("title", "").trim()
                    val artist = item.optJSONObject("artist")?.optString("name", "")?.trim() ?: "UNKNOWN ARTIST"
                    val album = item.optJSONObject("album")?.optString("title", "")?.trim() ?: title
                    val durationSec = item.optInt("duration", 200)
                    val durMinutes = durationSec / 60
                    val durSeconds = durationSec % 60
                    val durFormatted = String.format("%d:%02d", durMinutes, durSeconds)
                    val previewUrl = item.optString("preview", "")
                    val coverXl = item.optJSONObject("album")?.optString("cover_xl", "")
                        ?: item.optJSONObject("album")?.optString("cover_big", "")
                        ?: ""
                    val trackId = item.optLong("id", 0L).toString()

                    if (title.isNotBlank()) {
                        tracks.add(
                            Track(
                                title = title.uppercase(),
                                artist = artist.uppercase(),
                                album = album.uppercase(),
                                duration = durFormatted,
                                videoId = trackId,
                                thumbnailUrl = coverXl,
                                audioUrl = previewUrl
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        return tracks
    }

    suspend fun resolveAudioUrl(title: String, artist: String, rawLink: String = ""): String? = withContext(Dispatchers.IO) {
        if (rawLink.isNotBlank()) {
            val resolved = ArtistGridService.resolveTrackAudio(listOf(TrackLink(rawLink)))
            if (!resolved.isNullOrBlank()) {
                return@withContext resolved
            }
        }
        null
    }

    private fun searchInnerTube(query: String): List<Track> {
        val results = mutableListOf<Track>()
        var conn: HttpURLConnection? = null
        try {
            val url = URL(YTM_SEARCH_URL)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val requestJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                    })
                })
                put("query", query)
                // Filter to Songs
                put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                results.addAll(parseInnerTubeResponse(responseStr))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        return results
    }

    private fun parseInnerTubeResponse(jsonStr: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val root = JSONObject(jsonStr)

            fun scan(obj: Any) {
                when (obj) {
                    is JSONObject -> {
                        if (obj.has("musicResponsiveListItemRenderer")) {
                            val r = obj.getJSONObject("musicResponsiveListItemRenderer")
                            var title = ""
                            val artistsList = mutableListOf<String>()
                            var album = ""
                            var duration = "3:30"
                            var videoId = ""
                            var thumbUrl = ""

                            if (r.has("playlistItemData")) {
                                videoId = r.getJSONObject("playlistItemData").optString("videoId", "")
                            }

                            if (r.has("flexColumns")) {
                                val cols = r.getJSONArray("flexColumns")
                                if (cols.length() > 0) {
                                    val c0 = cols.getJSONObject(0).optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    val runs = c0?.optJSONObject("text")?.optJSONArray("runs")
                                    if (runs != null && runs.length() > 0) {
                                        val firstRun = runs.getJSONObject(0)
                                        title = firstRun.optString("text", "").trim()
                                        if (videoId.isEmpty()) {
                                            videoId = firstRun.optJSONObject("navigationEndpoint")
                                                ?.optJSONObject("watchEndpoint")
                                                ?.optString("videoId", "") ?: ""
                                        }
                                    }
                                }

                                for (cIdx in 1 until cols.length()) {
                                    val colObj = cols.getJSONObject(cIdx).optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    val runs = colObj?.optJSONObject("text")?.optJSONArray("runs") ?: continue
                                    for (i in 0 until runs.length()) {
                                        val runObj = runs.getJSONObject(i)
                                        val text = runObj.optString("text", "").trim()
                                        if (text.isEmpty() || text == "•" || text == "," || text == "&") continue

                                        val pageType = runObj.optJSONObject("navigationEndpoint")
                                            ?.optJSONObject("browseEndpoint")
                                            ?.optJSONObject("browseEndpointContextSupportedConfigs")
                                            ?.optJSONObject("browseEndpointContextMusicConfig")
                                            ?.optString("pageType", "") ?: ""

                                        when {
                                            pageType == "MUSIC_PAGE_TYPE_ARTIST" -> {
                                                if (!artistsList.contains(text)) artistsList.add(text)
                                            }
                                            pageType == "MUSIC_PAGE_TYPE_ALBUM" -> {
                                                if (album.isEmpty()) album = text
                                            }
                                            DURATION_REGEX.matches(text) -> {
                                                duration = text
                                            }
                                            text in listOf("Song", "Video", "Single", "EP") -> {}
                                            text.endsWith("plays") || text.endsWith("views") -> {}
                                            artistsList.isEmpty() -> {
                                                artistsList.add(text)
                                            }
                                            album.isEmpty() -> {
                                                album = text
                                            }
                                        }
                                    }
                                }
                            }

                            if (r.has("thumbnail")) {
                                val thumbs = r.getJSONObject("thumbnail")
                                    .optJSONObject("musicThumbnailRenderer")
                                    ?.optJSONObject("thumbnail")
                                    ?.optJSONArray("thumbnails")
                                if (thumbs != null && thumbs.length() > 0) {
                                    val rawUrl = thumbs.getJSONObject(thumbs.length() - 1).optString("url", "")
                                    thumbUrl = upgradeThumbnailToHq(rawUrl, videoId)
                                }
                            }
                            if (thumbUrl.isEmpty() && videoId.isNotEmpty()) {
                                thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            }

                            val artistStr = if (artistsList.isNotEmpty()) {
                                artistsList.joinToString(", ")
                            } else {
                                "UNKNOWN ARTIST"
                            }

                            val albumStr = if (album.isNotBlank()) album else title

                            if (title.isNotEmpty() && videoId.isNotEmpty()) {
                                tracks.add(
                                    Track(
                                        title = title.uppercase(),
                                        artist = artistStr.uppercase(),
                                        album = albumStr.uppercase(),
                                        duration = duration,
                                        videoId = videoId,
                                        thumbnailUrl = thumbUrl
                                    )
                                )
                            }
                        }

                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            scan(obj.get(keys.next()))
                        }
                    }
                    is JSONArray -> {
                        for (i in 0 until obj.length()) {
                            scan(obj.get(i))
                        }
                    }
                }
            }

            scan(root)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tracks.distinctBy { it.videoId }
    }

    private fun upgradeThumbnailToHq(rawUrl: String, videoId: String): String {
        return when {
            rawUrl.contains("googleusercontent.com") -> {
                rawUrl.replace(Regex("=(w|s)\\d+(-h\\d+)?.*"), "=w1000-h1000-l90-rj")
            }
            rawUrl.contains("ytimg.com") -> {
                rawUrl.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/hqdefault.jpg")
            }
            rawUrl.isEmpty() && videoId.isNotEmpty() -> {
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
            else -> rawUrl
        }
    }

    fun getDefaultTracks(filter: String = ""): List<Track> {
        val all = listOf(
            Track(
                title = "BLINDING LIGHTS",
                artist = "THE WEEKND",
                album = "AFTER HOURS",
                duration = "3:20",
                videoId = "908604612",
                thumbnailUrl = "https://cdn-images.dzcdn.net/images/cover/fd00ebd6d30d7253f813dba3bb1c66a9/1000x1000-000000-80-0-0.jpg",
                audioUrl = "https://cdnt-preview.dzcdn.net/api/1/1/1/b/2/0/1b27825bf63c36edcdc7fac9f920214e.mp3?hdnea=exp=1792187476~acl=/api/1/1/1/b/2/0/1b27825bf63c36edcdc7fac9f920214e.mp3*~data=user_id=0,application_id=42~hmac=5a448f9b4d1d37ecfef588a99d4db36d6ca048a4e1ea1cc3982231182e64e578"
            ),
            Track(
                title = "STARBOY",
                artist = "THE WEEKND",
                album = "STARBOY",
                duration = "3:50",
                videoId = "136889400",
                thumbnailUrl = "https://cdn-images.dzcdn.net/images/cover/134778e4c4f19ea71c82408300925a9a/1000x1000-000000-80-0-0.jpg",
                audioUrl = "https://cdnt-preview.dzcdn.net/api/1/1/3/c/1/0/3c19a82aeffd2f6f6adb63c4d8548e06.mp3?hdnea=exp=1792187476~acl=/api/1/1/3/c/1/0/3c19a82aeffd2f6f6adb63c4d8548e06.mp3*~data=user_id=0,application_id=42~hmac=4dcc272a721a2bffa91ccb91f955470e3351eeec5a78376be022243714012097"
            ),
            Track(
                title = "GET LUCKY",
                artist = "DAFT PUNK FEAT. PHARRELL",
                album = "RANDOM ACCESS MEMORIES",
                duration = "6:07",
                videoId = "67238735",
                thumbnailUrl = "https://cdn-images.dzcdn.net/images/cover/311bba0fc112d15f72c8b5a65f0456c1/1000x1000-000000-80-0-0.jpg",
                audioUrl = "https://cdnt-preview.dzcdn.net/api/1/1/c/8/a/0/c8a61130657a2cf58e3ac751e7950617.mp3?hdnea=exp=1792187477~acl=/api/1/1/c/8/a/0/c8a61130657a2cf58e3ac751e7950617.mp3*~data=user_id=0,application_id=42~hmac=da219e3c7814efeefb3478f8d648925d096774a9c34f72d2a91c6942a0423f7d"
            ),
            Track(
                title = "ONE MORE TIME",
                artist = "DAFT PUNK",
                album = "DISCOVERY",
                duration = "5:20",
                videoId = "3135553",
                thumbnailUrl = "https://cdn-images.dzcdn.net/images/cover/5718f7c81c27e0b2417e2a4c45224f8a/1000x1000-000000-80-0-0.jpg",
                audioUrl = "https://cdnt-preview.dzcdn.net/api/1/1/f/8/c/0/f8c5dc3837912dba37c9a1ab3170cc3f.mp3?hdnea=exp=1792187477~acl=/api/1/1/f/8/c/0/f8c5dc3837912dba37c9a1ab3170cc3f.mp3*~data=user_id=0,application_id=42~hmac=ff6d97c8d019a708022d389f6693cbf63db426d0b53b0cd86fde60cd03cb5493"
            ),
            Track(
                title = "MIDNIGHT CITY",
                artist = "M83",
                album = "HURRY UP, WE'RE DREAMING",
                duration = "4:04",
                videoId = "92198180",
                thumbnailUrl = "https://cdn-images.dzcdn.net/images/cover/17116bb5bc20e30d915e569ec750f78d/1000x1000-000000-80-0-0.jpg",
                audioUrl = "https://cdnt-preview.dzcdn.net/api/1/1/b/d/7/0/bd727b30df15d3d506abd829ee8da9f0.mp3?hdnea=exp=1792187477~acl=/api/1/1/b/d/7/0/bd727b30df15d3d506abd829ee8da9f0.mp3*~data=user_id=0,application_id=42~hmac=e1d4b2119cab33f7ff8125ef6688d397b5d6416009d58b9862f5e25e844fcd8d"
            ),
            Track(
                title = "NIGHTCALL",
                artist = "KAVINSKY",
                album = "OUTRUN",
                duration = "4:19",
                videoId = "62496771",
                thumbnailUrl = "https://cdn-images.dzcdn.net/images/cover/7f9ecda862091716e4e8f74a7e115f93/1000x1000-000000-80-0-0.jpg",
                audioUrl = "https://cdnt-preview.dzcdn.net/api/1/1/4/e/d/0/4edc468ce1e68847d08f5f968f5292ea.mp3?hdnea=exp=1792187478~acl=/api/1/1/4/e/d/0/4edc468ce1e68847d08f5f968f5292ea.mp3*~data=user_id=0,application_id=42~hmac=babf33bbd944eb05e1169ee7ac7f3db856efb748bc90886e0f653b3454a50913"
            )
        )
        if (filter.isEmpty()) return all
        return all.filter {
            it.title.contains(filter, ignoreCase = true) ||
            it.artist.contains(filter, ignoreCase = true) ||
            it.album.contains(filter, ignoreCase = true)
        }
    }
}
