package com.dk.tvplayer.data.parser

import com.dk.tvplayer.data.local.TvChannelEntity
import java.io.InputStream
import java.net.URLDecoder
import java.util.regex.Pattern

data class M3uEntry(
    val id: String?,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val streamUrl: String,
    val userAgent: String? = null,
    val referrer: String? = null
)

/** Per-entry HTTP headers pulled from VLC/Kodi-style playlist directives. */
private data class M3uHttpHeaders(val userAgent: String?, val referrer: String?)

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

    // VLC reads per-channel HTTP overrides from "#EXTVLCOPT:http-user-agent=..." and
    // "#EXTVLCOPT:http-referrer=..." lines placed between #EXTINF and the stream URL.
    // Large aggregated playlists (e.g. iptv-org's combined index.m3u) rely heavily on
    // these — many of the CDNs behind those channels reject requests that don't carry
    // a matching User-Agent/Referer, which is exactly why they show up as "won't load"
    // in players that (like this one used to) silently drop the tag instead of
    // honouring it the way VLC/Kodi do.
    private val EXTVLCOPT_UA_PATTERN =
        Pattern.compile("(?i)#EXTVLCOPT:\\s*http-user-agent\\s*=\\s*(.+)")
    private val EXTVLCOPT_REFERRER_PATTERN =
        Pattern.compile("(?i)#EXTVLCOPT:\\s*http-referr?er\\s*=\\s*(.+)")

    /**
     * A default, browser-like User-Agent applied when a channel doesn't specify its
     * own. VLC always identifies itself with *some* User-Agent string; a handful of
     * CDNs reject requests carrying no User-Agent at all (or a bare "okhttp/x.y")
     * outright, so sending a normal-looking one by default costs nothing and quietly
     * fixes a class of otherwise-unexplained failures.
     */
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** Generic parse used for playlist imports (Playlists tab) and other non-channel uses. */
    fun parseEntries(inputStream: InputStream): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        val reader = inputStream.bufferedReader()
        var currentLine: String?
        var tempId: String? = null
        var tempName: String? = null
        var tempLogo: String? = null
        var tempGroup: String? = null
        var tempUserAgent: String? = null
        var tempReferrer: String? = null

        while (reader.readLine().also { currentLine = it } != null) {
            val line = currentLine!!.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:")) {
                tempId = extract(TVG_ID_PATTERN, line)
                tempName = extract(TVG_NAME_PATTERN, line)
                tempLogo = extract(TVG_LOGO_PATTERN, line)
                tempGroup = extract(GROUP_TITLE_PATTERN, line)
                tempUserAgent = null
                tempReferrer = null

                val titleIndex = line.lastIndexOf(',')
                val displayTitle = if (titleIndex != -1) line.substring(titleIndex + 1).trim() else "Item"
                if (tempName.isNullOrEmpty()) {
                    tempName = displayTitle
                }
            } else if (line.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                extract(EXTVLCOPT_UA_PATTERN, line)?.let { tempUserAgent = it.trim() }
                extract(EXTVLCOPT_REFERRER_PATTERN, line)?.let { tempReferrer = it.trim() }
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                val resolvedName = tempName
                if (!resolvedName.isNullOrEmpty()) {
                    val (cleanUrl, pipeHeaders) = splitPipedHeaders(line)
                    entries.add(
                        M3uEntry(
                            id = tempId,
                            name = resolvedName,
                            logoUrl = tempLogo,
                            groupTitle = tempGroup,
                            streamUrl = cleanUrl,
                            userAgent = pipeHeaders.userAgent ?: tempUserAgent,
                            referrer = pipeHeaders.referrer ?: tempReferrer
                        )
                    )
                }
                tempId = null
                tempName = null
                tempLogo = null
                tempGroup = null
                tempUserAgent = null
                tempReferrer = null
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
        var tempUserAgent: String? = null
        var tempReferrer: String? = null
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
                tempUserAgent = null
                tempReferrer = null

                val titleIndex = line.lastIndexOf(',')
                val displayTitle = if (titleIndex != -1) line.substring(titleIndex + 1).trim() else "Channel"
                if (tempName.isNullOrEmpty()) {
                    tempName = displayTitle
                }
            } else if (line.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                // VLC-specific per-channel HTTP overrides — see field docs above. Ignored
                // by everything else in this parser (harmless), but critical for playback
                // of many channels in large aggregated playlists like iptv-org's index.m3u.
                extract(EXTVLCOPT_UA_PATTERN, line)?.let { tempUserAgent = it.trim() }
                extract(EXTVLCOPT_REFERRER_PATTERN, line)?.let { tempReferrer = it.trim() }
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
                    val (cleanUrl, pipeHeaders) = splitPipedHeaders(line)
                    channels.add(
                        TvChannelEntity(
                            channelId = resolvedId,
                            name = resolvedName,
                            logoUrl = tempLogo,
                            groupTitle = tempGroup ?: "General",
                            streamUrl = cleanUrl,
                            userAgent = pipeHeaders.userAgent ?: tempUserAgent,
                            referrer = pipeHeaders.referrer ?: tempReferrer
                        )
                    )
                }
                tempId = null
                tempName = null
                tempLogo = null
                tempGroup = null
                tempUserAgent = null
                tempReferrer = null
            }
        }
        return channels
    }

    /**
     * Some playlists (Kodi's PVR IPTV Simple Client convention, also used by a number
     * of iptv-org-style sources) attach headers directly to the stream URL instead of
     * a preceding #EXTVLCOPT line, e.g.:
     *   https://example.com/stream.m3u8|User-Agent=Foo&Referer=https://example.com/
     * Splits that off and returns the bare playable URL plus any headers found.
     */
    private fun splitPipedHeaders(rawLine: String): Pair<String, M3uHttpHeaders> {
        val pipeIndex = rawLine.indexOf('|')
        if (pipeIndex == -1) return rawLine to M3uHttpHeaders(null, null)

        val cleanUrl = rawLine.substring(0, pipeIndex).trim()
        val paramString = rawLine.substring(pipeIndex + 1)
        var userAgent: String? = null
        var referrer: String? = null
        for (pair in paramString.split('&')) {
            val eq = pair.indexOf('=')
            if (eq == -1) continue
            val key = pair.substring(0, eq).trim()
            val rawValue = pair.substring(eq + 1).trim()
            val value = runCatching { URLDecoder.decode(rawValue, "UTF-8") }.getOrDefault(rawValue)
            when {
                key.equals("User-Agent", ignoreCase = true) -> userAgent = value
                key.equals("Referer", ignoreCase = true) || key.equals("Referrer", ignoreCase = true) -> referrer = value
            }
        }
        return cleanUrl to M3uHttpHeaders(userAgent, referrer)
    }

    private fun extract(pattern: Pattern, line: String): String? {
        val matcher = pattern.matcher(line)
        return if (matcher.find()) matcher.group(1) else null
    }
}
