package com.dk.tvplayer.data.backup

import com.dk.tvplayer.data.local.AppSettings
import com.dk.tvplayer.data.local.AppThemeMode
import com.dk.tvplayer.data.local.PlaylistItemEntity
import com.dk.tvplayer.data.local.SortOption
import com.dk.tvplayer.data.local.StreamEntity
import org.json.JSONArray
import org.json.JSONObject

data class BackupPlaylist(
    val name: String,
    val items: List<PlaylistItemEntity>
)

data class BackupBundle(
    val settings: AppSettings,
    val customStreams: List<StreamEntity>,
    val playlists: List<BackupPlaylist>
)

/**
 * Serializes/deserializes a [BackupBundle] to JSON using the platform's built-in
 * org.json — no extra serialization dependency required. Used by the Settings
 * screen's Export/Import feature.
 */
object SettingsBackupManager {

    private const val SCHEMA_VERSION = 1

    fun serialize(bundle: BackupBundle): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)

        val settingsJson = JSONObject().apply {
            put("hwAcceleration", bundle.settings.hwAcceleration)
            put("backgroundAudioPlayback", bundle.settings.backgroundAudioPlayback)
            put("autoResumePlayback", bundle.settings.autoResumePlayback)
            put("sortOption", bundle.settings.sortOption.name)
            put("themeMode", bundle.settings.themeMode.name)
            put("themeSeedColor", bundle.settings.themeSeedColor)
            put("defaultPlaybackSpeed", bundle.settings.defaultPlaybackSpeed)
        }
        root.put("settings", settingsJson)

        val streamsArray = JSONArray()
        bundle.customStreams.forEach { stream ->
            streamsArray.put(
                JSONObject().apply {
                    put("name", stream.name)
                    put("streamUrl", stream.streamUrl)
                    put("groupTitle", stream.groupTitle)
                    put("logoUrl", stream.logoUrl ?: JSONObject.NULL)
                }
            )
        }
        root.put("customStreams", streamsArray)

        val playlistsArray = JSONArray()
        bundle.playlists.forEach { playlist ->
            val itemsArray = JSONArray()
            playlist.items.forEach { item ->
                itemsArray.put(
                    JSONObject().apply {
                        put("title", item.title)
                        put("mediaUrl", item.mediaUrl)
                        put("logoUrl", item.logoUrl ?: JSONObject.NULL)
                        put("groupTitle", item.groupTitle ?: JSONObject.NULL)
                        put("position", item.position)
                    }
                )
            }
            playlistsArray.put(
                JSONObject().apply {
                    put("name", playlist.name)
                    put("items", itemsArray)
                }
            )
        }
        root.put("playlists", playlistsArray)

        return root.toString(2)
    }

    fun deserialize(json: String): BackupBundle {
        val root = JSONObject(json)
        val settingsJson = root.optJSONObject("settings") ?: JSONObject()

        val settings = AppSettings(
            hwAcceleration = settingsJson.optBoolean("hwAcceleration", true),
            backgroundAudioPlayback = settingsJson.optBoolean("backgroundAudioPlayback", false),
            autoResumePlayback = settingsJson.optBoolean("autoResumePlayback", true),
            sortOption = runCatching {
                SortOption.valueOf(settingsJson.optString("sortOption", SortOption.NAME_ASC.name))
            }.getOrDefault(SortOption.NAME_ASC),
            themeMode = runCatching {
                AppThemeMode.valueOf(settingsJson.optString("themeMode", AppThemeMode.DARK.name))
            }.getOrDefault(AppThemeMode.DARK),
            themeSeedColor = settingsJson.optLong("themeSeedColor", 0xFFB39DDB),
            defaultPlaybackSpeed = settingsJson.optDouble("defaultPlaybackSpeed", 1.0).toFloat()
        )

        val streams = mutableListOf<StreamEntity>()
        val streamsArray = root.optJSONArray("customStreams") ?: JSONArray()
        for (i in 0 until streamsArray.length()) {
            val obj = streamsArray.getJSONObject(i)
            streams.add(
                StreamEntity(
                    name = obj.optString("name"),
                    streamUrl = obj.optString("streamUrl"),
                    groupTitle = obj.optString("groupTitle", "Custom Streams"),
                    logoUrl = obj.optString("logoUrl", null).takeUnless { obj.isNull("logoUrl") }
                )
            )
        }

        val playlists = mutableListOf<BackupPlaylist>()
        val playlistsArray = root.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until playlistsArray.length()) {
            val obj = playlistsArray.getJSONObject(i)
            val itemsArray = obj.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<PlaylistItemEntity>()
            for (j in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(j)
                items.add(
                    PlaylistItemEntity(
                        playlistId = 0L, // resolved by caller once the playlist row is inserted
                        title = itemObj.optString("title"),
                        mediaUrl = itemObj.optString("mediaUrl"),
                        logoUrl = itemObj.optString("logoUrl", null).takeUnless { itemObj.isNull("logoUrl") },
                        groupTitle = itemObj.optString("groupTitle", null).takeUnless { itemObj.isNull("groupTitle") },
                        position = itemObj.optInt("position", j)
                    )
                )
            }
            playlists.add(BackupPlaylist(name = obj.optString("name"), items = items))
        }

        return BackupBundle(settings = settings, customStreams = streams, playlists = playlists)
    }
}
