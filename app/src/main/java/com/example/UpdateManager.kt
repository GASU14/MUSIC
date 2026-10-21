package com.example

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateManager {
    private const val GITHUB_REPO_URL = "https://api.github.com/repos/GASU14/MUSIC/releases/latest"
    const val CURRENT_VERSION = "v0.1-alpha"

    suspend fun checkLatestVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_REPO_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            json.getString("tag_name")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadAndUpdate(context: Context, downloadUrl: String) = withContext(Dispatchers.IO) {
        try {
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("Updating Music App")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "update.apk")
            
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)
            
            // In a real scenario, you'd listen for the DOWNLOAD_COMPLETE broadcast to trigger the install.
            // For this applet, keeping it simple:
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
