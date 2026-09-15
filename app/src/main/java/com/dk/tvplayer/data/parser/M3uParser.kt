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

    // Compiling a regex is expensive relative to matching it — for a 15k-40k channel
    // playlist, recompiling 4 patterns per line (as this used to do) meant 60k-160k+
    // Pattern.compile() calls, which dominated parse time and made large playlists
    // feel like they'd hung. Compiling each pattern exactly once here and reusing it
    // for every line is the single biggest speedup for large playlists.
    private val TVG_ID_PATTERN = Pattern.compile("tvg-id=\"([^\"]*)\"")
    private val TVG_NAME_PATTERN = Pattern.compile("tvg-name=\"([^\"]*)\"")
    private val TVG_LOGO_PATTERN = Pattern.compile("tvg-logo=\"([^\"]*)\"")
    private val GROUP_TITLE_PATTERN = Pattern.compile("group-title=\"([^\"]*)\"")

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
                tempId = extract(TVG_ID_PATTERN, line)
                tempName = extract(TVG_NAME_PATTERN, line)
                tempLogo = extract(TVG_LOGO_PATTERN, line)
                tempGroup = extract(GROUP_TITLE_PATTERN, line)

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
        // Guards against duplicate/missing tvg-id values producing colliding generated
        // channel IDs — extremely common in large combined playlists (many entries
        // share a name with no tvg-id at all). A colliding ID isn't just cosmetic: it's
        // used as a list key in Compose, and a duplicate key there crashes the UI
        // outright once the list is large enough for a collision to actually occur.
        val usedChannelIds = HashSet<String>()

        while (reader.readLine().also { currentLine = it } != null) {
            val line = currentLine!!.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:")) {
                tempId = extract(TVG_ID_PATTERN, line)
                tempName = extract(TVG_NAME_PATTERN, line)
                tempLogo = extract(TVG_LOGO_PATTERN, line)
                tempGroup = extract(GROUP_TITLE_PATTERN, line)

                val titleIndex = line.lastIndexOf(',')
                val displayTitle = if (titleIndex != -1) line.substring(titleIndex + 1).trim() else "Channel"
                if (tempName.isNullOrEmpty()) {
                    tempName = displayTitle
                }
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                val resolvedName = tempName
                if (!resolvedName.isNullOrEmpty()) {
                    var resolvedId = tempId?.takeIf { it.isNotBlank() }
                        ?: resolvedName.lowercase().replace(" ", "_")
                    // De-duplicate: append a running suffix if this ID has already been
                    // used by an earlier channel in this same playlist.
                    if (!usedChannelIds.add(resolvedId)) {
                        var suffix = 2
                        var candidate = "${resolvedId}_$suffix"
                        while (!usedChannelIds.add(candidate)) {
                            suffix++
                            candidate = "${resolvedId}_$suffix"
                        }
                        resolvedId = candidate
                    }
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

    private fun extract(pattern: Pattern, line: String): String? {
        val matcher = pattern.matcher(line)
        return if (matcher.find()) matcher.group(1) else null
    }
}
