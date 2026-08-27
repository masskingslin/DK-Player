package com.dk.tvplayer.data.repository

import com.dk.tvplayer.data.local.HistoryDao
import com.dk.tvplayer.data.local.HistoryEntity
import com.dk.tvplayer.data.local.LocalVideoItem
import com.dk.tvplayer.data.local.LocalVideoScanner
import com.dk.tvplayer.data.local.StreamDao
import com.dk.tvplayer.data.local.StreamEntity
import com.dk.tvplayer.data.local.TvChannelDao
import com.dk.tvplayer.data.local.TvChannelEntity
import com.dk.tvplayer.data.local.TvEpgDao
import com.dk.tvplayer.data.local.TvEpgProgramEntity
import com.dk.tvplayer.data.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream

class TvRepository(
    private val channelDao: TvChannelDao,
    private val epgDao: TvEpgDao,
    private val historyDao: HistoryDao,
    private val streamDao: StreamDao,
    private val scanner: LocalVideoScanner
) {
    fun getAllChannels(): Flow<List<TvChannelEntity>> = channelDao.getAllChannels()
    fun getAllGroups(): Flow<List<String>> = channelDao.getAllGroups()
    fun getPrograms(channelId: String): Flow<List<TvEpgProgramEntity>> =
        epgDao.getProgramsForChannel(channelId, System.currentTimeMillis())

    fun getHistory(): Flow<List<HistoryEntity>> = historyDao.getRecentHistory()
    fun getCustomStreams(): Flow<List<StreamEntity>> = streamDao.getAllStreams()

    suspend fun insertCustomStream(name: String, url: String) = withContext(Dispatchers.IO) {
        streamDao.insertStream(StreamEntity(name = name, streamUrl = url))
    }

    suspend fun deleteCustomStream(stream: StreamEntity) = withContext(Dispatchers.IO) {
        streamDao.deleteStream(stream)
    }

    suspend fun saveHistory(url: String, title: String, position: Long, duration: Long) =
        withContext(Dispatchers.IO) {
            historyDao.insertOrUpdate(
                HistoryEntity(
                    mediaUrl = url,
                    title = title,
                    lastPositionMs = position,
                    durationMs = duration
                )
            )
        }

    suspend fun loadM3u(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val parsed = M3uParser.parse(inputStream)
        channelDao.clearChannels()
        channelDao.insertChannels(parsed)
    }

    suspend fun scanLocalVideos(): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        scanner.scanDeviceVideos()
    }
}
