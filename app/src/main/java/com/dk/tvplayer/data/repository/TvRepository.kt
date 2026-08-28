package com.dk.tvplayer.data.repository

import android.content.Context
import android.net.Uri
import com.dk.tvplayer.data.local.ChannelEntity
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.data.local.TvDao
import com.dk.tvplayer.data.model.AppBackupData
import com.dk.tvplayer.data.model.PlaylistBackupDto
import com.dk.tvplayer.data.model.StreamBackupDto
import com.dk.tvplayer.data.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class TvRepository(
    private val context: Context,
    private val tvDao: TvDao
) {
    fun getFilteredChannels(
        query: String?,
        category: String?,
        onlyFavorites: Boolean,
        sortBy: String
    ): Flow<List<ChannelEntity>> = tvDao.getFilteredChannels(query, category, onlyFavorites, sortBy)

    fun getAllCategories(): Flow<List<String>> = tvDao.getAllCategories()

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = tvDao.getAllPlaylists()

    suspend fun toggleFavorite(channel: ChannelEntity) = withContext(Dispatchers.IO) {
        tvDao.updateChannel(channel.copy(isFavorite = !channel.isFavorite))
    }

    suspend fun recordChannelPlayed(channel: ChannelEntity) = withContext(Dispatchers.IO) {
        tvDao.updateChannel(channel.copy(lastPlayedTimestamp = System.currentTimeMillis()))
    }

    suspend fun batchDeleteChannels(channelIds: List<Long>) = withContext(Dispatchers.IO) {
        tvDao.deleteChannelsByIds(channelIds)
    }

    suspend fun batchMoveChannels(channelIds: List<Long>, targetCategory: String) = withContext(Dispatchers.IO) {
        tvDao.moveChannelsToGroup(channelIds, targetCategory)
    }

    suspend fun addPlaylist(title: String, pathOrUrl: String, isLocal: Boolean) = withContext(Dispatchers.IO) {
        val playlistId = tvDao.insertPlaylist(
            PlaylistEntity(
                title = title,
                urlOrPath = pathOrUrl,
                isLocalFile = isLocal,
                lastUpdated = System.currentTimeMillis()
            )
        )
        syncPlaylist(playlistId, pathOrUrl, isLocal)
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        tvDao.deleteChannelsForPlaylist(playlist.id)
        tvDao.deletePlaylist(playlist)
    }

    suspend fun syncPlaylist(playlistId: Long, pathOrUrl: String, isLocal: Boolean) = withContext(Dispatchers.IO) {
        try {
            val content = if (isLocal) {
                File(pathOrUrl).readText()
            } else {
                java.net.URL(pathOrUrl).readText()
            }
            val parsedChannels = M3uParser.parse(content)
            val entities = parsedChannels.mapIndexed { idx, parsed ->
                ChannelEntity(
                    name = parsed.name,
                    url = parsed.url,
                    logoUrl = parsed.logoUrl,
                    groupTitle = parsed.groupTitle ?: "General",
                    epgChannelId = parsed.tvgId,
                    orderIndex = idx,
                    playlistId = playlistId
                )
            }
            tvDao.deleteChannelsForPlaylist(playlistId)
            tvDao.insertChannels(entities)
        } catch (_: Exception) { }
    }

    suspend fun exportDataToJson(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val allChannels = tvDao.getAllChannelsSync().map {
                StreamBackupDto(it.name, it.url, it.groupTitle, it.logoUrl, it.isFavorite)
            }
            val allPlaylists = tvDao.getAllPlaylistsSync().map {
                PlaylistBackupDto(it.title, it.urlOrPath, it.isLocalFile)
            }
            val backup = AppBackupData(
                customStreams = allChannels,
                playlists = allPlaylists
            )
            val jsonText = Json { prettyPrint = true }.encodeToString(backup)
            context.contentResolver.openOutputStream(uri)?.use { it.write(jsonText.toByteArray()) }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importDataFromJson(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonText = context.contentResolver.openInputStream(uri)?.use {
                it.reader().readText()
            } ?: return@withContext false

            val backup = Json.decodeFromString<AppBackupData>(jsonText)
            val entities = backup.customStreams.mapIndexed { idx, s ->
                ChannelEntity(
                    name = s.name,
                    url = s.url,
                    groupTitle = s.groupTitle,
                    logoUrl = s.logoUrl,
                    isFavorite = s.isFavorite,
                    orderIndex = idx
                )
            }
            tvDao.insertChannels(entities)
            true
        } catch (e: Exception) {
            false
        }
    }
}
