package com.dk.tvplayer.data.parser

import com.dk.tvplayer.data.local.TvChannelEntity
import java.io.InputStream
import java.util.regex.Pattern

object M3uParser {
    fun parse(inputStream: InputStream): List<TvChannelEntity> {
        val channels = mutableListOf<TvChannelEntity>()
        val reader = inputStream.bufferedReader()
        var currentLine: String?
        var tempId: String? = null
        var tempName: String? = null
        var tempLogo: String? = null
        var tempGroup: String? = null

        while (reader.readLine().also { currentLine = it } != null) {
            val line = currentLine!!.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:")) {
                tempId = extractAttribute(line, "tvg-id")
                tempName = extractAttribute(line, "tvg-name")
                tempLogo = extractAttribute(line, "tvg-logo")
                tempGroup = extractAttribute(line, "group-title")

                val titleIndex = line.lastIndexOf(',')
                val displayTitle = if (titleIndex != -1) line.substring(titleIndex + 1).trim() else "Channel"
                if (tempName.isNullOrEmpty()) tempName = displayTitle
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                if (!tempName.isNullOrEmpty()) {
                    channels.add(
                        TvChannelEntity(
                            channelId = tempId ?: tempName.lowercase().replace(" ", "_"),
                            name = tempName,
                            logoUrl = tempLogo,
                            groupTitle = tempGroup ?: "General",
                            streamUrl = line
                        )
                    )
                }
                tempId = null
                tempName = null
                tempLogo = null
                tempGroup = null
            }
        }
        return channels
    }

    private fun extractAttribute(line: String, attrName: String): String? {
        val pattern = Pattern.compile("$attrName=\"([^\"]*)\"")
        val matcher = pattern.matcher(line)
        return if (matcher.find()) matcher.group(1) else null
    }
}
