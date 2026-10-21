package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class Artist(
    val name: String,
    val url: String,
    val credit: String = "",
    val linksWork: Boolean = true,
    val isBest: Boolean = false,
    val imageUrl: String = "",
    val trackerId: String = ""
)

data class ProjectEra(
    val name: String,
    val timeline: String = "",
    val description: String = "",
    val coverArt: String = "",
    val color: String = "",
    val textColor: String = "",
    val tracks: List<ProjectTrack> = emptyList()
)

data class ProjectTrack(
    val rawName: String,
    val title: String,
    val credits: List<String> = emptyList(),
    val notes: String = "",
    val quality: String = "",
    val availableLength: String = "",
    val leakDate: String = "",
    val fileDate: String = "",
    val links: List<TrackLink> = emptyList()
)

data class TrackLink(
    val url: String,
    val text: String = ""
)

data class TrackerData(
    val name: String,
    val currentTab: String,
    val tabs: List<TrackerTab>,
    val eras: List<ProjectEra>
)

data class TrackerTab(
    val name: String,
    val slug: String,
    val gid: String = ""
)

object ArtistGridService {

    private const val ARTISTS_CSV_URL = "https://artists.artistgrid.cx/artists.csv"
    private const val TRACKER_API_BASE = "https://trackerapi.artistgrid.cx"
    private const val ASSETS_BASE = "https://assets.artistgrid.cx"

    private val PUBHTML_REGEX = Pattern.compile("""/spreadsheets/d/e/(2PACX-[a-zA-Z0-9_-]+)/""")
    private val SHEET_ID_REGEX = Pattern.compile("""/spreadsheets(?:/u/\d+)?/d/([a-zA-Z0-9_-]{20,})""")
    private val BARE_ID_REGEX = Pattern.compile("""^(2PACX-[a-zA-Z0-9_-]+|[a-zA-Z0-9_-]{20,})$""")

    private val SPECIAL_TRACKER_HOSTS = setOf(
        "yetracker.net",
        "asaprockytracker.net",
        "deftonestracker.net",
        "franktracker.net",
        "tylertracker.net",
        "travistracker.com",
        "cartitracker.com",
        "uzitracker.com"
    )

    // In-memory caches for snappy UI performance
    private var cachedArtists: List<Artist>? = null
    private var cachedAssetMap: Map<String, String>? = null
    private val trackerCache = ConcurrentHashMap<String, TrackerData>()

    suspend fun getArtists(query: String = ""): List<Artist> = withContext(Dispatchers.IO) {
        val all = if (cachedArtists != null) {
            cachedArtists!!
        } else {
            val fetched = fetchArtistsFromApi()
            val list = if (fetched.isNotEmpty()) fetched else getFallbackArtists()
            cachedArtists = list
            list
        }

        if (query.isBlank()) {
            all
        } else {
            val q = query.trim().lowercase()
            all.filter { it.name.lowercase().contains(q) }
        }
    }

    private suspend fun fetchArtistsFromApi(): List<Artist> {
        val artists = mutableListOf<Artist>()
        var conn: HttpURLConnection? = null
        try {
            val assetMap = getAssetIndex()

            val url = URL(ARTISTS_CSV_URL)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val lines = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readLines() }
                // First line is header: name,url,credit,links_work,updated,best
                val dataLines = if (lines.isNotEmpty() && lines[0].startsWith("name,")) lines.drop(1) else lines

                for (line in dataLines) {
                    val tokens = parseCsvLine(line)
                    if (tokens.size >= 2) {
                        val name = tokens[0].trim()
                        val rawUrl = tokens[1].trim()
                        val credit = if (tokens.size > 2) tokens[2].trim() else ""
                        val linksWork = if (tokens.size > 3) tokens[3].trim() != "0" else true
                        val isBest = if (tokens.size > 5) tokens[5].trim().equals("true", ignoreCase = true) else false

                        if (name.isNotBlank() && rawUrl.isNotBlank()) {
                            val trackerId = extractTrackerId(rawUrl)
                            val slug = slugify(name)
                            val cdnFile = assetMap[slug]
                            val imageUrl = if (cdnFile != null) "$ASSETS_BASE/og/$cdnFile" else ""

                            artists.add(
                                Artist(
                                    name = name,
                                    url = rawUrl,
                                    credit = credit,
                                    linksWork = linksWork,
                                    isBest = isBest,
                                    imageUrl = imageUrl,
                                    trackerId = trackerId
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        return artists
    }

    private suspend fun getAssetIndex(): Map<String, String> {
        if (cachedAssetMap != null) return cachedAssetMap!!
        val map = mutableMapOf<String, String>()
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$ASSETS_BASE/index.json")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val filename = array.optString(i, "")
                    if (filename.isNotBlank()) {
                        val baseName = filename.substringBeforeLast('.')
                        val slug = slugify(baseName)
                        map[slug] = filename
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        cachedAssetMap = map
        return map
    }

    suspend fun getTrackerData(trackerId: String, tabSlug: String? = null): TrackerData? = withContext(Dispatchers.IO) {
        if (trackerId.isBlank()) return@withContext null
        val cacheKey = "$trackerId::${tabSlug ?: "default"}"
        trackerCache[cacheKey]?.let { return@withContext it }

        var conn: HttpURLConnection? = null
        try {
            val endpoint = if (tabSlug != null) {
                "$TRACKER_API_BASE/sh/$trackerId/tab/$tabSlug"
            } else {
                "$TRACKER_API_BASE/sh/$trackerId/"
            }

            val url = URL(endpoint)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val parsed = parseTrackerResponse(jsonStr)
                if (parsed != null) {
                    trackerCache[cacheKey] = parsed
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        null
    }

    private fun parseTrackerResponse(jsonStr: String): TrackerData? {
        try {
            val root = JSONObject(jsonStr)
            val trackerName = root.optString("name", "Tracker")

            val currentTabSlug = root.optJSONObject("tab")?.optString("slug", "main") ?: "main"

            val tabsList = mutableListOf<TrackerTab>()
            val tabsArray = root.optJSONArray("tabs") ?: JSONArray()
            for (i in 0 until tabsArray.length()) {
                val tObj = tabsArray.optJSONObject(i) ?: continue
                tabsList.add(
                    TrackerTab(
                        name = tObj.optString("name", ""),
                        slug = tObj.optString("slug", ""),
                        gid = tObj.optString("gid", "")
                    )
                )
            }

            val erasList = mutableListOf<ProjectEra>()

            // 1. Try structured eras
            val erasArray = root.optJSONArray("eras")
            if (erasArray != null && erasArray.length() > 0) {
                for (i in 0 until erasArray.length()) {
                    val eObj = erasArray.optJSONObject(i) ?: continue
                    val eraName = eObj.optString("name", "Untitled Era")
                    val timeline = eObj.optString("timeline", "")
                    val description = eObj.optString("description", "")
                    val rawCover = eObj.optString("cover_art", "")
                    val coverArt = toHqCoverUrl(rawCover)
                    val color = eObj.optString("color", "")
                    val textColor = eObj.optString("text_color", "")

                    val tracksArray = eObj.optJSONArray("tracks") ?: JSONArray()
                    val tracksList = mutableListOf<ProjectTrack>()
                    for (tIdx in 0 until tracksArray.length()) {
                        val tObj = tracksArray.optJSONObject(tIdx) ?: continue
                        tracksList.add(parseTrackObject(tObj))
                    }

                    erasList.add(
                        ProjectEra(
                            name = eraName,
                            timeline = timeline,
                            description = description,
                            coverArt = coverArt,
                            color = color,
                            textColor = textColor,
                            tracks = tracksList
                        )
                    )
                }
            } else {
                // 2. Try flat tracks list
                val flatTracksArray = root.optJSONArray("tracks")
                if (flatTracksArray != null && flatTracksArray.length() > 0) {
                    val grouped = mutableMapOf<String, MutableList<ProjectTrack>>()
                    for (i in 0 until flatTracksArray.length()) {
                        val tObj = flatTracksArray.optJSONObject(i) ?: continue
                        val eraName = tObj.optString("era", "Uncategorized").ifBlank { "Uncategorized" }
                        val track = parseTrackObject(tObj)
                        grouped.getOrPut(eraName) { mutableListOf() }.add(track)
                    }

                    for ((eraName, tList) in grouped) {
                        erasList.add(
                            ProjectEra(
                                name = eraName,
                                timeline = "",
                                description = "",
                                coverArt = "",
                                tracks = tList
                            )
                        )
                    }
                }
            }

            return TrackerData(
                name = trackerName,
                currentTab = currentTabSlug,
                tabs = tabsList,
                eras = erasList
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseTrackObject(tObj: JSONObject): ProjectTrack {
        val nameObj = tObj.optJSONObject("name")
        val rawName = nameObj?.optString("raw", "") ?: tObj.optString("name", "Unknown Track")
        val title = nameObj?.optString("title", "").let { if (it.isNullOrBlank()) rawName else it }

        val creditsList = mutableListOf<String>()
        val creditsArr = nameObj?.optJSONArray("credits")
        if (creditsArr != null) {
            for (c in 0 until creditsArr.length()) {
                val cr = creditsArr.optString(c, "").trim()
                if (cr.isNotEmpty()) creditsList.add(cr)
            }
        }

        val notes = tObj.optString("notes", "")
        val quality = tObj.optString("quality", "")
        val availableLength = tObj.optString("available_length", "")
        val leakDate = tObj.optString("leak_date", "")
        val fileDate = tObj.optString("file_date", "")

        val linksList = mutableListOf<TrackLink>()
        val linksArr = tObj.optJSONArray("links")
        if (linksArr != null) {
            for (l in 0 until linksArr.length()) {
                val lItem = linksArr.opt(l)
                if (lItem is JSONObject) {
                    val lUrl = lItem.optString("url", "").trim()
                    val lText = lItem.optString("text", "").trim()
                    if (lUrl.isNotEmpty()) linksList.add(TrackLink(lUrl, lText))
                } else if (lItem is String && lItem.isNotBlank()) {
                    linksList.add(TrackLink(lItem.trim()))
                }
            }
        }

        return ProjectTrack(
            rawName = rawName,
            title = title,
            credits = creditsList,
            notes = notes,
            quality = quality,
            availableLength = availableLength,
            leakDate = leakDate,
            fileDate = fileDate,
            links = linksList
        )
    }

    fun extractTrackerId(url: String): String {
        val trimmed = url.trim()
        val withoutProto = trimmed.removePrefix("https://").removePrefix("http://").removeSuffix("/")
        if (withoutProto in SPECIAL_TRACKER_HOSTS) return withoutProto

        val pubhtmlMatcher = PUBHTML_REGEX.matcher(trimmed)
        if (pubhtmlMatcher.find()) return pubhtmlMatcher.group(1) ?: trimmed

        val sheetMatcher = SHEET_ID_REGEX.matcher(trimmed)
        if (sheetMatcher.find()) return sheetMatcher.group(1) ?: trimmed

        val bareMatcher = BARE_ID_REGEX.matcher(trimmed)
        if (bareMatcher.find()) return bareMatcher.group(1) ?: trimmed

        return withoutProto
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (i in 0 until line.length) {
            val ch = line[i]
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.setLength(0)
            } else {
                sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    private fun slugify(name: String): String {
        return name.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun getFallbackArtists(): List<Artist> {
        return listOf(
            Artist("Kanye West", "yetracker.net", "Ye Community", true, true, "$ASSETS_BASE/og/kanyewest.png", "yetracker.net"),
            Artist("A\$AP Rocky", "asaprockytracker.net", "KILLRITE, Digital Hendryk", true, true, "$ASSETS_BASE/og/asaprocky.png", "asaprockytracker.net"),
            Artist("Frank Ocean", "franktracker.net", "Infishere, Edideaur", true, true, "$ASSETS_BASE/og/frankocean.png", "franktracker.net"),
            Artist("Tyler, The Creator", "tylertracker.net", "Tyler Tracker Team", true, true, "$ASSETS_BASE/og/tylerthecreator.png", "tylertracker.net"),
            Artist("Dave Blunts", "1FyFFSiTma6zcqtuYaldoZg0HUoEip7xwDSEReL7JmH4", "awsum, HEAT DEATH", true, true, "$ASSETS_BASE/og/daveblunts.png", "1FyFFSiTma6zcqtuYaldoZg0HUoEip7xwDSEReL7JmH4"),
            Artist("Deftones", "deftonestracker.net", "Troabroa, Brimcoole", true, true, "$ASSETS_BASE/og/deftones.png", "deftonestracker.net"),
            Artist("Playboi Carti", "148YxG900o4o28qFzepk78Fk4t8vF2Z_n", "Carti Tracker Team", true, true, "$ASSETS_BASE/og/playboicarti.png", "148YxG900o4o28qFzepk78Fk4t8vF2Z_n"),
            Artist("Travis Scott", "1nAMP5s-l4o_vSj9v54FpB9mE5X9e9s", "Cactus Jack Trackers", true, true, "$ASSETS_BASE/og/travisscott.png", "1nAMP5s-l4o_vSj9v54FpB9mE5X9e9s"),
            Artist("Kendrick Lamar", "1mQ8zB9k8v0sO4l", "pgLang / TDE Trackers", true, true, "$ASSETS_BASE/og/kendricklamar.png", "1mQ8zB9k8v0sO4l")
        )
    }

    suspend fun resolveTrackAudio(links: List<TrackLink>): String? = withContext(Dispatchers.IO) {
        for (link in links) {
            val url = link.url.trim()
            if (url.isBlank()) continue

            // 1. Direct audio files (.mp3, .m4a, .wav, .flac, .ogg, .aac)
            val lower = url.lowercase()
            if (lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav") ||
                lower.endsWith(".flac") || lower.endsWith(".ogg") || lower.endsWith(".aac") ||
                lower.contains(".mp3?") || lower.contains(".m4a?") || lower.contains(".wav?")) {
                return@withContext url
            }

            // 2. Imgur.gg audio host (used by thousands of tracker tracks)
            if (url.contains("imgur.gg/f/") || url.contains("imgur.gg/")) {
                val fileId = url.substringAfter("imgur.gg/f/").substringAfter("imgur.gg/").substringBefore("/").substringBefore("?").trim()
                if (fileId.isNotBlank()) {
                    val resolved = fetchImgurAudioUrl(fileId)
                    if (!resolved.isNullOrBlank()) return@withContext resolved
                }
            }

            // 3. Pixeldrain host
            if (url.contains("pixeldrain.com/u/")) {
                val fileId = url.substringAfter("pixeldrain.com/u/").substringBefore("/").substringBefore("?").trim()
                if (fileId.isNotBlank()) {
                    return@withContext "https://pixeldrain.com/api/file/$fileId"
                }
            }

            // 4. Google Drive direct export
            if (url.contains("drive.google.com/file/d/")) {
                val fileId = url.substringAfter("drive.google.com/file/d/").substringBefore("/").substringBefore("?").trim()
                if (fileId.isNotBlank()) {
                    return@withContext "https://docs.google.com/uc?export=download&id=$fileId"
                }
            }

            // 5. Pillowcase / pillows.su
            if (url.contains("pillows.su/f/") || url.contains("pillowcase.su/f/") || url.contains("pillowcase.zip/f/")) {
                val fileId = url.substringAfter("/f/").substringBefore("/").substringBefore("?").trim()
                if (fileId.isNotBlank()) {
                    val candidate = "https://api.pillows.su/api/download/$fileId"
                    if (testUrlAvailable(candidate)) {
                        return@withContext candidate
                    }
                }
            }

            // 6. KrakenFiles
            if (url.contains("krakenfiles.com/view/")) {
                val resolved = fetchKrakenAudioUrl(url)
                if (!resolved.isNullOrBlank()) return@withContext resolved
            }
        }
        null
    }

    private fun fetchImgurAudioUrl(fileId: String): String? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://imgur.gg/api/file/$fileId")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val root = JSONObject(jsonStr)
                val cdnUrl = root.optString("cdnUrl", "").trim()
                if (cdnUrl.isNotBlank()) return cdnUrl
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        return null
    }

    private fun fetchKrakenAudioUrl(pageUrl: String): String? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(pageUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val html = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val match = Regex("""<source[^>]+src=[\"\']([^\"\']+)[\"\']""").find(html)
                    ?: Regex("""<audio[^>]+src=[\"\']([^\"\']+)[\"\']""").find(html)
                if (match != null) {
                    var src = match.groupValues[1]
                    if (src.startsWith("//")) src = "https:$src"
                    return src
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        return null
    }

    private fun testUrlAvailable(testUrl: String): Boolean {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(testUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val code = conn.responseCode
            return code in 200..399
        } catch (_: Exception) {
            return false
        } finally {
            conn?.disconnect()
        }
    }

    fun toHqCoverUrl(url: String): String {
        if (url.isBlank()) return ""
        var hq = url
        if (hq.contains("googleusercontent.com") || hq.contains("ggpht.com") || hq.contains("ytimg.com")) {
            hq = hq.replace(Regex("=w\\d+-h\\d+.*$"), "=w1000-h1000")
            hq = hq.replace(Regex("=s\\d+.*$"), "=s1000")
            hq = hq.replace("hqdefault.jpg", "maxresdefault.jpg")
            hq = hq.replace("mqdefault.jpg", "maxresdefault.jpg")
            hq = hq.replace("sddefault.jpg", "maxresdefault.jpg")
        }
        return hq
    }
}

