package com.stream.tvplayer.data.parser

import com.stream.tvplayer.data.local.ChannelEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uParser {
    private val EXTINF_REGEX = Regex(
        """#EXTINF:-?\d+\s*(?:tvg-id="([^"]*)")?\s*(?:tvg-name="([^"]*)")?\s*(?:tvg-logo="([^"]*)")?\s*(?:group-title="([^"]*)")?[^,]*,\s*(.*)"""
    )

    fun parseStream(
        inputStream: InputStream,
        onBatchParsed: suspend (List<ChannelEntity>) -> Unit
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val batch = mutableListOf<ChannelEntity>()
        var line: String?
        var currentTvgId: String? = null
        var currentName: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var channelIndex = 1

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (trimmed.startsWith("#EXTINF")) {
                val match = EXTINF_REGEX.find(trimmed)
                if (match != null) {
                    val (tvgId, tvgName, tvgLogo, groupTitle, title) = match.destructured
                    currentTvgId = tvgId.ifBlank { null }
                    currentName = if (title.isNotBlank()) title.trim() else tvgName.trim()
                    currentLogo = tvgLogo.ifBlank { null }
                    currentGroup = groupTitle.ifBlank { null }
                } else {
                    currentName = trimmed.substringAfter(",", "Channel $channelIndex").trim()
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                if (currentName != null) {
                    batch.add(
                        ChannelEntity(
                            channelNumber = channelIndex++,
                            name = currentName,
                            streamUrl = trimmed,
                            tvgId = currentTvgId ?: currentName,
                            logoUrl = currentLogo,
                            groupName = currentGroup
                        )
                    )
                    currentTvgId = null
                    currentName = null
                    currentLogo = null
                    currentGroup = null

                    if (batch.size >= 500) {
                        kotlinx.coroutines.runBlocking { onBatchParsed(batch.toList()) }
                        batch.clear()
                    }
                }
            }
        }
        if (batch.isNotEmpty()) {
            kotlinx.coroutines.runBlocking { onBatchParsed(batch.toList()) }
            batch.clear()
        }
    }
}
