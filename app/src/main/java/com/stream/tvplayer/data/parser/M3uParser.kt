package com.stream.tvplayer.data.parser

import com.stream.tvplayer.data.local.ChannelEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

object M3uParser {

    private val EXTINF_PATTERN = Pattern.compile("(?i)#EXTINF:(?:-1|[0-9]+)(?:\\s+(.*))?,(.*)")
    private val TVG_ID_PATTERN = Pattern.compile("(?i)tvg-id=\"([^\"]*)\"")
    private val TVG_NAME_PATTERN = Pattern.compile("(?i)tvg-name=\"([^\"]*)\"")
    private val TVG_LOGO_PATTERN = Pattern.compile("(?i)tvg-logo=\"([^\"]*)\"")
    private val GROUP_TITLE_PATTERN = Pattern.compile("(?i)group-title=\"([^\"]*)\"")
    private val TVG_CHNO_PATTERN = Pattern.compile("(?i)tvg-chno=\"([^\"]*)\"")

    suspend fun parseStream(
        inputStream: InputStream,
        batchSize: Int = 100,
        onBatchParsed: suspend (List<ChannelEntity>) -> Unit
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        var channelIndex = 1
        val batch = mutableListOf<ChannelEntity>()

        var currentTvgId: String? = null
        var currentName: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentChannelNumber: Int? = null

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line?.trim() ?: continue
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                val matcher = EXTINF_PATTERN.matcher(trimmed)
                if (matcher.find()) {
                    val attributes = matcher.group(1) ?: ""
                    val title = matcher.group(2)?.trim() ?: ""

                    currentTvgId = extractAttribute(TVG_ID_PATTERN, attributes)
                    val tvgName = extractAttribute(TVG_NAME_PATTERN, attributes)
                    currentName = if (!tvgName.isNullOrBlank()) tvgName else title
                    currentLogo = extractAttribute(TVG_LOGO_PATTERN, attributes)
                    currentGroup = extractAttribute(GROUP_TITLE_PATTERN, attributes) ?: "General"
                    currentChannelNumber = extractAttribute(TVG_CHNO_PATTERN, attributes)?.toIntOrNull()
                }
            } else if (!trimmed.startsWith("#")) {
                if (!currentName.isNullOrBlank()) {
                    val entity = ChannelEntity(
                        channelNumber = currentChannelNumber ?: channelIndex,
                        name = currentName,
                        logoUrl = currentLogo,
                        streamUrl = trimmed,
                        groupName = currentGroup ?: "General",
                        tvgId = currentTvgId ?: currentName,
                        isFavorite = false
                    )
                    batch.add(entity)
                    channelIndex++

                    if (batch.size >= batchSize) {
                        onBatchParsed(batch.toList())
                        batch.clear()
                    }
                }
                currentTvgId = null
                currentName = null
                currentLogo = null
                currentGroup = null
                currentChannelNumber = null
            }
        }

        if (batch.isNotEmpty()) {
            onBatchParsed(batch.toList())
            batch.clear()
        }
    }

    private fun extractAttribute(pattern: Pattern, text: String): String? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }
}
