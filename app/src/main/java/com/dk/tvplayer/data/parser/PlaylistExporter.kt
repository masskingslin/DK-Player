package com.dk.tvplayer.data.parser

import com.dk.tvplayer.data.local.PlaylistItemEntity

/**
 * Serializes playlist items back out to standard M3U (#EXTM3U/#EXTINF) and
 * XSPF (XML) formats so playlists created/edited in-app can be exported and
 * reused in other players.
 */
object PlaylistExporter {

    fun toM3u(items: List<PlaylistItemEntity>): String {
        val builder = StringBuilder()
        builder.append("#EXTM3U\n")
        items.sortedBy { it.position }.forEach { item ->
            val attrs = StringBuilder()
            if (!item.logoUrl.isNullOrBlank()) {
                attrs.append(" tvg-logo=\"${item.logoUrl}\"")
            }
            if (!item.groupTitle.isNullOrBlank()) {
                attrs.append(" group-title=\"${item.groupTitle}\"")
            }
            builder.append("#EXTINF:-1$attrs,${item.title}\n")
            builder.append("${item.mediaUrl}\n")
        }
        return builder.toString()
    }

    fun toXspf(playlistName: String, items: List<PlaylistItemEntity>): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">\n")
        builder.append("  <title>${xmlEscape(playlistName)}</title>\n")
        builder.append("  <trackList>\n")
        items.sortedBy { it.position }.forEach { item ->
            builder.append("    <track>\n")
            builder.append("      <location>${xmlEscape(item.mediaUrl)}</location>\n")
            builder.append("      <title>${xmlEscape(item.title)}</title>\n")
            if (!item.logoUrl.isNullOrBlank()) {
                builder.append("      <image>${xmlEscape(item.logoUrl)}</image>\n")
            }
            if (!item.groupTitle.isNullOrBlank()) {
                builder.append("      <annotation>${xmlEscape(item.groupTitle)}</annotation>\n")
            }
            builder.append("    </track>\n")
        }
        builder.append("  </trackList>\n")
        builder.append("</playlist>\n")
        return builder.toString()
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
