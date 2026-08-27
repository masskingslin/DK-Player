package com.stream.tvplayer.data.repository

import com.stream.tvplayer.data.local.ChannelEntity
import com.stream.tvplayer.data.local.EpgEntity
import com.stream.tvplayer.data.local.HistoryDao
import com.stream.tvplayer.data.local.HistoryEntity
import com.stream.tvplayer.data.local.StreamDao
import com.stream.tvplayer.data.local.StreamEntity
import com.stream.tvplayer.data.local.TvDao
import com.stream.tvplayer.data.parser.M3uParser
import com.stream.tvplayer.data.parser.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.URL

class TvRepository(
    private val tvDao: TvDao,
    private val historyDao: HistoryDao,
    private val streamDao: StreamDao
) {

    fun getChannelsStream(): Flow<List<ChannelEntity>> = tvDao.getAllChannels()

    suspend fun syncPlaylist(url: String) = withContext(Dispatchers.IO) {
        val stream = URL(url).openStream()
        tvDao.clearChannels()
        stream.use { input ->
            M3uParser.parseStream(input) { batch ->
                tvDao.insertChannels(batch)
            }
        }
    }

    suspend fun syncEpg(url: String) = withContext(Dispatchers.IO) {
        val stream = URL(url).openStream()
        tvDao.clearExpiredEpg(System.currentTimeMillis())
        stream.use { input ->
            XmlTvParser.parseStream(input) { batch ->
                tvDao.insertEpgPrograms(batch)
            }
        }
    }

    suspend fun getLiveSchedule(channelTvgId: String): Pair<EpgEntity?, EpgEntity?> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val current = tvDao.getCurrentProgram(channelTvgId, now)
            val next = tvDao.getNextProgram(channelTvgId, current?.endEpochMs ?: now)
            Pair(current, next)
        }

    // --- History ---

    fun getHistoryStream(): Flow<List<HistoryEntity>> = historyDao.getHistoryStream()

    suspend fun saveHistoryEntry(entry: HistoryEntity): Long =
        withContext(Dispatchers.IO) { historyDao.upsert(entry) }

    suspend fun deleteHistoryEntry(id: Long) =
        withContext(Dispatchers.IO) { historyDao.delete(id) }

    suspend fun clearHistory() =
        withContext(Dispatchers.IO) { historyDao.clearAll() }

    // --- Streams ---

    fun getStreamsFlow(): Flow<List<StreamEntity>> = streamDao.getStreamsFlow()

    suspend fun addStream(name: String, url: String): Long =
        withContext(Dispatchers.IO) {
            streamDao.insert(StreamEntity(name = name, url = url, savedAt = System.currentTimeMillis()))
        }

    suspend fun deleteStream(id: Long) =
        withContext(Dispatchers.IO) { streamDao.delete(id) }
}