@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dk.tvplayer.player

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.flac.VorbisComment
import com.dk.tvplayer.util.PlaybackPrefs
import com.dk.tvplayer.util.ReplayGainMode
import kotlin.math.log10
import kotlin.math.pow

/**
 * Replay gain: evens out loudness using the ReplayGain tags in audio files (ID3, Vorbis/FLAC
 * and MP4 freeform tags). Attenuation is applied through the player volume; boosts through a
 * LoudnessEnhancer, limited by the track's peak when "Peak protection" is on.
 *
 * Gain (dB) = tag value + (target level − 89 dB). Files without tags use "Default replay gain"
 * (0 = leave untouched).
 */
class ReplayGain(private val player: ExoPlayer) {
    private var trackGain: Float? = null
    private var albumGain: Float? = null
    private var trackPeak: Float? = null
    private var albumPeak: Float? = null

    private var enhancer: LoudnessEnhancer? = null
    private var enhancerSession = 0

    /** A new item started: forget the previous item's tags and apply the default. */
    fun onNewItem() {
        trackGain = null; albumGain = null; trackPeak = null; albumPeak = null
        apply()
    }

    fun onTracksChanged(tracks: Tracks) {
        tracks.groups.forEach { group ->
            if (group.type != androidx.media3.common.C.TRACK_TYPE_AUDIO) return@forEach
            for (i in 0 until group.length) group.getTrackFormat(i).metadata?.let { read(it) }
        }
        apply()
    }

    fun onMetadata(metadata: Metadata) {
        read(metadata)
        apply()
    }

    /** Re-evaluates with the current settings (call when a setting changes). */
    fun apply() {
        if (!PlaybackPrefs.replayGainEnabled.value) {
            player.volume = 1f
            enhancer?.enabled = false
            return
        }
        val useAlbum = PlaybackPrefs.replayGainMode.value == ReplayGainMode.ALBUM
        val tagGain = if (useAlbum) albumGain ?: trackGain else trackGain ?: albumGain
        val peak = if (useAlbum) albumPeak ?: trackPeak else trackPeak ?: albumPeak

        var gainDb = if (tagGain != null) {
            tagGain + (PlaybackPrefs.replayPreampDb.value - 89)
        } else {
            PlaybackPrefs.defaultReplayGainDb.value.toFloat()
        }
        if (PlaybackPrefs.peakProtection.value && peak != null && peak > 0f && gainDb > 0f) {
            gainDb = minOf(gainDb, -20f * log10(peak))
        }

        if (gainDb <= 0f) {
            player.volume = 10f.pow(gainDb / 20f).coerceIn(0f, 1f)
            enhancer?.enabled = false
        } else {
            player.volume = 1f
            boost(gainDb)
        }
    }

    fun release() {
        runCatching { enhancer?.release() }
        enhancer = null
        player.volume = 1f
    }

    private fun boost(gainDb: Float) {
        val session = player.audioSessionId
        if (session == 0) return
        if (enhancer == null || enhancerSession != session) {
            runCatching { enhancer?.release() }
            enhancer = runCatching { LoudnessEnhancer(session) }.getOrNull()
            enhancerSession = session
        }
        enhancer?.let {
            runCatching {
                it.setTargetGain((gainDb * 100).toInt().coerceAtMost(1500))
                it.enabled = true
            }
        }
    }

    private fun read(metadata: Metadata) {
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is TextInformationFrame ->
                    if (entry.id == "TXXX") store(entry.description, entry.values.firstOrNull())
                is VorbisComment -> store(entry.key, entry.value)
                is InternalFrame -> store(entry.description, entry.text)
                else -> Unit
            }
        }
    }

    private fun store(name: String?, raw: String?) {
        val value = raw?.let { Regex("-?\\d+(\\.\\d+)?").find(it)?.value?.toFloatOrNull() } ?: return
        when (name?.uppercase()) {
            "REPLAYGAIN_TRACK_GAIN" -> trackGain = value
            "REPLAYGAIN_ALBUM_GAIN" -> albumGain = value
            "REPLAYGAIN_TRACK_PEAK" -> trackPeak = value
            "REPLAYGAIN_ALBUM_PEAK" -> albumPeak = value
        }
    }
}
