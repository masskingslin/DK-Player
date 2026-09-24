package com.dk.tvplayer.data.local

import java.util.regex.Pattern

/** What VideoLibraryScreen actually renders: either a standalone video, or a group
 *  (manually created, or auto-suggested from similar filenames) containing several. */
sealed interface LocalVideoDisplayItem {
    val key: String

    data class Single(val video: LocalVideoItem, val isPlayed: Boolean) : LocalVideoDisplayItem {
        override val key: String get() = "single:${video.filePath}"
    }

    /**
     * [group] is null for an auto-suggested grouping that hasn't been confirmed by any
     * user action yet (rename/ungroup/add-to) — at that point it exists only in this
     * computed list, not the DB. The first such action on it persists a real
     * [VideoGroupEntity] behind the scenes (see TvPlayerViewModel), after which [group]
     * is non-null and [key] switches from the name-derived key to a stable id-based one.
     */
    data class Group(
        val group: VideoGroupEntity?,
        val autoName: String,
        val videos: List<LocalVideoItem>,
        val playedCount: Int
    ) : LocalVideoDisplayItem {
        val displayName: String get() = group?.name ?: autoName
        override val key: String get() = group?.let { "group:${it.id}" } ?: "autogroup:$autoName"
    }
}

/**
 * Builds the combined list VideoLibraryScreen shows: manually grouped videos first
 * (one Group entry per distinct groupId), then auto-suggested groups among the
 * remaining ungrouped videos (VLC groups similarly-named files — episodes, parts —
 * automatically; a group of 1 is pointless, so a "group" of size 1 just renders as a
 * Single), then any leftover standalone videos.
 */
object LocalVideoGrouping {

    fun buildDisplayItems(
        videos: List<LocalVideoItem>,
        groups: List<VideoGroupEntity>,
        meta: List<LocalVideoMetaEntity>
    ): List<LocalVideoDisplayItem> {
        val metaByPath = meta.associateBy { it.filePath }
        val groupsById = groups.associateBy { it.id }
        val result = mutableListOf<LocalVideoDisplayItem>()

        val manuallyGrouped = videos.filter { metaByPath[it.filePath]?.groupId != null }
        val ungrouped = videos - manuallyGrouped.toSet()

        manuallyGrouped.groupBy { metaByPath[it.filePath]!!.groupId!! }.forEach { (groupId, members) ->
            val group = groupsById[groupId]
            if (group != null) {
                result += LocalVideoDisplayItem.Group(
                    group = group,
                    autoName = group.name,
                    videos = members,
                    playedCount = members.count { metaByPath[it.filePath]?.isPlayed == true }
                )
            } else {
                // Group row was deleted but member rows weren't cleaned up (shouldn't
                // normally happen — ungroupVideos clears both — but fall back to
                // treating them as standalone rather than dropping them silently).
                members.forEach { result += it.toSingle(metaByPath) }
            }
        }

        ungrouped.groupBy { autoGroupKey(it.name)?.matchKey }.forEach { (matchKey, members) ->
            if (matchKey != null && members.size >= 2) {
                val displayName = autoGroupKey(members.first().name)!!.displayName
                result += LocalVideoDisplayItem.Group(
                    group = null,
                    autoName = displayName,
                    videos = members.sortedBy { it.name },
                    playedCount = members.count { metaByPath[it.filePath]?.isPlayed == true }
                )
            } else {
                members.forEach { result += it.toSingle(metaByPath) }
            }
        }

        return result
    }

    private fun LocalVideoItem.toSingle(metaByPath: Map<String, LocalVideoMetaEntity>) =
        LocalVideoDisplayItem.Single(this, metaByPath[filePath]?.isPlayed == true)

    // Season/episode markers: S01E01, S1E1, 1x01. Part markers: "Part 1", "Part1", "Pt.2",
    // "CD1". A lone trailing number (" 1", " 2", "-3"). A trailing 4-digit year in
    // parens/brackets. Common quality/codec tags often present on scene-release-style
    // filenames, which would otherwise defeat matching between two parts encoded
    // differently. Order matters: strip the more specific patterns first.
    private val SEASON_EPISODE = Pattern.compile("(?i)[ ._-]*S\\d{1,2}E\\d{1,3}")
    private val EPISODE_X = Pattern.compile("(?i)[ ._-]*\\d{1,2}x\\d{1,3}")
    private val PART_MARKER = Pattern.compile("(?i)[ ._-]*(part|pt\\.?|cd|disc|disk)[ ._-]?\\d{1,2}\\b")
    private val TRAILING_NUMBER = Pattern.compile("[ ._-]+\\d{1,3}$")
    private val YEAR_TAG = Pattern.compile("[\\[(]((19|20)\\d{2})[\\])]")
    private val QUALITY_TAG = Pattern.compile(
        "(?i)[\\[(]?\\b(480p|720p|1080p|2160p|4k|hdr|hevc|x264|x265|h264|h265|web[- ]?dl|bluray|hdtv|dvdrip)\\b[\\])]?"
    )
    private val BRACKETED = Pattern.compile("[\\[(][^\\])]{1,30}[\\])]")

    /** Cleaned name for grouping: [displayName] keeps original casing/spacing for
     *  showing to the user, [matchKey] is its lowercase form used to actually match
     *  files against each other (so "Show S01E01" and "SHOW S01E02" still group). */
    private data class AutoGroupKey(val displayName: String, val matchKey: String)

    /** Returns a normalized grouping key derived from a filename, or null if the name
     *  is too short/generic once stripped to safely auto-group on (avoids grouping
     *  unrelated files that happen to share a short common prefix like "Video"). */
    private fun autoGroupKey(rawName: String): AutoGroupKey? {
        var name = rawName.substringBeforeLast('.', rawName) // drop extension
        name = SEASON_EPISODE.matcher(name).replaceAll("")
        name = EPISODE_X.matcher(name).replaceAll("")
        name = PART_MARKER.matcher(name).replaceAll("")
        name = QUALITY_TAG.matcher(name).replaceAll("")
        name = YEAR_TAG.matcher(name).replaceAll("")
        name = BRACKETED.matcher(name).replaceAll("")
        name = TRAILING_NUMBER.matcher(name).replaceAll("")
        name = name.replace(Regex("[._-]+"), " ").trim()
        return name.takeIf { it.length >= 4 }?.let { AutoGroupKey(it, it.lowercase()) }
    }
}
