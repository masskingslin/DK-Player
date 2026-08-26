package com.stream.tvplayer.data.repository

import com.stream.tvplayer.data.local.ChannelEntity
import com.stream.tvplayer.data.local.EpgEntity
import com.stream.tvplayer.data.local.TvDao
import com.stream.tvplayer.data.parser.M3uParser
import com.stream.tvplayer.data.parser.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.URL

class TvRepository(private val tvDao: TvDao) {

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
}
