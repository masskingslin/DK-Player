package com.dk.tvplayer.util

import android.content.Context
import com.dk.tvplayer.data.local.LocalAudioItem
import com.dk.tvplayer.data.local.LocalVideoItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The result of the last media scan, saved to disk. With "Auto rescan" off, the app shows this
 * saved list at startup instead of scanning the device again.
 */
object MediaListCache {
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun file(name: String) = appContext?.let { File(it.filesDir, name) }

    fun saveVideos(items: List<LocalVideoItem>) = write("cache_videos.json", JSONArray().apply {
        items.forEach {
            put(JSONObject().put("id", it.id).put("name", it.name).put("duration", it.duration)
                .put("path", it.filePath).put("size", it.size))
        }
    })

    fun loadVideos(): List<LocalVideoItem>? = read("cache_videos.json")?.let { arr ->
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LocalVideoItem(o.getLong("id"), o.getString("name"), o.getLong("duration"), o.getString("path"), o.getLong("size"))
        }.filter { File(it.filePath).exists() }
    }

    fun saveAudio(items: List<LocalAudioItem>) = write("cache_audio.json", JSONArray().apply {
        items.forEach {
            put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)
                .put("album", it.album).put("albumId", it.albumId).put("duration", it.duration)
                .put("path", it.filePath).put("size", it.size))
        }
    })

    fun loadAudio(): List<LocalAudioItem>? = read("cache_audio.json")?.let { arr ->
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LocalAudioItem(
                o.getLong("id"), o.getString("title"), o.getString("artist"), o.getString("album"),
                o.getLong("albumId"), o.getLong("duration"), o.getString("path"), o.getLong("size")
            )
        }.filter { File(it.filePath).exists() }
    }

    fun saveQueue(items: List<Pair<String, String>>) = write("cache_queue.json", JSONArray().apply {
        items.forEach { put(JSONObject().put("path", it.first).put("title", it.second)) }
    })

    fun loadQueue(): List<Pair<String, String>> = read("cache_queue.json")?.let { arr ->
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { it.getString("path") to it.getString("title") }
            .filter { File(it.first).exists() }
    } ?: emptyList()

    private fun write(name: String, arr: JSONArray) {
        runCatching { file(name)?.writeText(arr.toString()) }
    }

    private fun read(name: String): JSONArray? =
        runCatching { file(name)?.takeIf { it.exists() }?.readText()?.let { JSONArray(it) } }.getOrNull()
}
