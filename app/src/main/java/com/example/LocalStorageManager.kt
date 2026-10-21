package com.example

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val iconPath: String = "",
    val tracks: List<Track> = emptyList()
)

data class DownloadedSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val duration: String = "3:30",
    val localFilePath: String,
    val coverUrl: String = "",
    val fileSizeMb: Float = 0f,
    val addedTime: Long = System.currentTimeMillis()
)

object LocalStorageManager {

    private const val PLAYLISTS_FILE = "playlists.json"
    private const val DOWNLOADS_FILE = "downloads.json"

    fun getDownloadsDir(context: Context): File {
        val dir = File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPlaylistIconsDir(context: Context): File {
        val dir = File(context.filesDir, "playlist_icons")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // --- PLAYLISTS ---

    @Synchronized
    fun getPlaylists(context: Context): List<Playlist> {
        val file = File(context.filesDir, PLAYLISTS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<Playlist>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val trkArray = obj.optJSONArray("tracks") ?: JSONArray()
                val tracks = mutableListOf<Track>()
                for (j in 0 until trkArray.length()) {
                    val t = trkArray.getJSONObject(j)
                    tracks.add(
                        Track(
                            title = t.optString("title"),
                            artist = t.optString("artist"),
                            album = t.optString("album"),
                            duration = t.optString("duration", "3:30"),
                            videoId = t.optString("videoId"),
                            thumbnailUrl = t.optString("thumbnailUrl"),
                            audioUrl = t.optString("audioUrl")
                        )
                    )
                }
                list.add(
                    Playlist(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title"),
                        description = obj.optString("description"),
                        iconPath = obj.optString("iconPath"),
                        tracks = tracks
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun savePlaylists(context: Context, playlists: List<Playlist>) {
        val file = File(context.filesDir, PLAYLISTS_FILE)
        try {
            val array = JSONArray()
            for (p in playlists) {
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("title", p.title)
                obj.put("description", p.description)
                obj.put("iconPath", p.iconPath)
                val trkArray = JSONArray()
                for (t in p.tracks) {
                    val tobj = JSONObject()
                    tobj.put("title", t.title)
                    tobj.put("artist", t.artist)
                    tobj.put("album", t.album)
                    tobj.put("duration", t.duration)
                    tobj.put("videoId", t.videoId)
                    tobj.put("thumbnailUrl", t.thumbnailUrl)
                    tobj.put("audioUrl", t.audioUrl)
                    trkArray.put(tobj)
                }
                obj.put("tracks", trkArray)
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun copyIconToApp(context: Context, source: String, playlistId: String): String = withContext(Dispatchers.IO) {
        try {
            val iconsDir = getPlaylistIconsDir(context)
            val ext = if (source.contains(".gif", ignoreCase = true)) ".gif" else ".png"
            val destFile = File(iconsDir, "playlist_${playlistId}$ext")

            if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) {
                val url = URL(source)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    return@withContext destFile.absolutePath
                }
            } else if (source.startsWith("content://") || source.startsWith("file://")) {
                val uri = Uri.parse(source)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext destFile.absolutePath
            }
            source
        } catch (e: Exception) {
            e.printStackTrace()
            source
        }
    }

    fun addTrackToPlaylist(context: Context, playlistId: String, track: Track) {
        val playlists = getPlaylists(context).toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val p = playlists[index]
            if (p.tracks.none { it.title.equals(track.title, ignoreCase = true) && it.artist.equals(track.artist, ignoreCase = true) }) {
                playlists[index] = p.copy(tracks = p.tracks + track)
                savePlaylists(context, playlists)
            }
        }
    }

    // --- DOWNLOADS ---

    @Synchronized
    fun getDownloadedSongs(context: Context): List<DownloadedSong> {
        val file = File(context.filesDir, DOWNLOADS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DownloadedSong>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val path = obj.optString("localFilePath")
                if (File(path).exists()) {
                    list.add(
                        DownloadedSong(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            artist = obj.optString("artist"),
                            album = obj.optString("album"),
                            duration = obj.optString("duration", "3:30"),
                            localFilePath = path,
                            coverUrl = obj.optString("coverUrl"),
                            fileSizeMb = obj.optDouble("fileSizeMb", 0.0).toFloat(),
                            addedTime = obj.optLong("addedTime", System.currentTimeMillis())
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun saveDownloadedSongs(context: Context, songs: List<DownloadedSong>) {
        val file = File(context.filesDir, DOWNLOADS_FILE)
        try {
            val array = JSONArray()
            for (s in songs) {
                val obj = JSONObject()
                obj.put("id", s.id)
                obj.put("title", s.title)
                obj.put("artist", s.artist)
                obj.put("album", s.album)
                obj.put("duration", s.duration)
                obj.put("localFilePath", s.localFilePath)
                obj.put("coverUrl", s.coverUrl)
                obj.put("fileSizeMb", s.fileSizeMb.toDouble())
                obj.put("addedTime", s.addedTime)
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isDownloaded(context: Context, trackKey: String): Boolean {
        val downloads = getDownloadedSongs(context)
        return downloads.any { it.id == trackKey }
    }

    suspend fun downloadTrackFile(
        context: Context,
        track: Track,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val audioUrl = if (track.audioUrl.isNotBlank() && track.audioUrl.startsWith("http")) {
                track.audioUrl
            } else if (track.links.isNotEmpty()) {
                ArtistGridService.resolveTrackAudio(track.links)
            } else if (track.videoId.isNotBlank()) {
                ArtistGridService.resolveTrackAudio(listOf(TrackLink(track.videoId)))
            } else null

            if (audioUrl.isNullOrBlank()) return@withContext false

            val trackId = "${track.title}_${track.artist}".replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
            val downloadsDir = getDownloadsDir(context)
            val destFile = File(downloadsDir, "$trackId.mp3")

            val url = URL(audioUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val totalLength = conn.contentLength.toFloat()
                var downloaded = 0
                conn.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalLength > 0) {
                                onProgress?.invoke(downloaded / totalLength)
                            }
                        }
                    }
                }

                val sizeMb = destFile.length() / (1024f * 1024f)
                val downloadedSong = DownloadedSong(
                    id = trackId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    duration = track.duration,
                    localFilePath = destFile.absolutePath,
                    coverUrl = track.thumbnailUrl,
                    fileSizeMb = sizeMb
                )

                val list = getDownloadedSongs(context).filterNot { it.id == trackId } + downloadedSong
                saveDownloadedSongs(context, list)
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteDownloadedSongs(context: Context, songIds: Set<String>) {
        val current = getDownloadedSongs(context)
        val toKeep = mutableListOf<DownloadedSong>()
        for (song in current) {
            if (songIds.contains(song.id)) {
                try {
                    File(song.localFilePath).delete()
                } catch (_: Exception) {}
            } else {
                toKeep.add(song)
            }
        }
        saveDownloadedSongs(context, toKeep)
    }

    fun clearAllDownloads(context: Context) {
        val downloadsDir = getDownloadsDir(context)
        downloadsDir.listFiles()?.forEach {
            try { it.delete() } catch (_: Exception) {}
        }
        saveDownloadedSongs(context, emptyList())
    }

    fun clearAppCache(context: Context) {
        try {
            context.cacheDir.deleteRecursively()
        } catch (_: Exception) {}
    }
}
