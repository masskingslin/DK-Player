package com.dk.tvplayer.data.parser

import com.dk.tvplayer.data.local.TvChannelEntity
import java.io.InputStream
import java.util.regex.Pattern

data class M3uEntry(
    val id: String?,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val streamUrl: String
)

object M3uParser {

    /** Generic parse used for playlist imports (Playlists tab) and other non-channel uses. */
    fun parseEntries(inputStream: InputStream): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
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
                val displayTitle = if (titleIndex != -1) line.substring(titleIndex + 1).trim() else "Item"
                if (tempName.isNullOrEmpty()) {
                    tempName = displayTitle
                }
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                val resolvedName = tempName
                if (!resolvedName.isNullOrEmpty()) {
                    entries.add(
                        M3uEntry(
                            id = tempId,
                            name = resolvedName,
                            logoUrl = tempLogo,
                            groupTitle = tempGroup,
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
        return entries
    }

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
                if (tempName.isNullOrEmpty()) {
                    tempName = displayTitle
                }
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                val resolvedName = tempName
                if (!resolvedName.isNullOrEmpty()) {
                    val resolvedId = tempId ?: resolvedName.lowercase().replace(" ", "_")
                    channels.add(
                        TvChannelEntity(
                            channelId = resolvedId,
                            name = resolvedName,
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
