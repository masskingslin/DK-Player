package com.dk.tvplayer.data.parser

import java.util.regex.Pattern

data class ParsedM3uChannel(
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val tvgId: String? = null
)

object M3uParser {
    fun parse(content: String): List<ParsedM3uChannel> {
        val channels = mutableListOf<ParsedM3uChannel>()
        val lines = content.lineSequence()
        var tempId: String? = null
        var tempName: String? = null
        var tempLogo: String? = null
        var tempGroup: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
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
                    channels.add(
                        ParsedM3uChannel(
                            name = resolvedName,
                            url = line,
                            logoUrl = tempLogo,
                            groupTitle = tempGroup,
                            tvgId = tempId
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
