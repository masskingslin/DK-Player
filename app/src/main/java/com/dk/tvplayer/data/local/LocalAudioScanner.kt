package com.dk.tvplayer.data.local

import android.content.Context
import android.provider.MediaStore

data class LocalAudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val filePath: String,
    val size: Long
)

class LocalAudioScanner(private val context: Context) {
    fun scanDeviceAudio(): List<LocalAudioItem> {
        val audioList = mutableListOf<LocalAudioItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (it.moveToNext()) {
                audioList.add(
                    LocalAudioItem(
                        id = it.getLong(idColumn),
                        title = it.getString(titleColumn) ?: "Unknown Title",
                        artist = it.getString(artistColumn) ?: "Unknown Artist",
                        album = it.getString(albumColumn) ?: "Unknown Album",
                        albumId = it.getLong(albumIdColumn),
                        duration = it.getLong(durationColumn),
                        filePath = it.getString(dataColumn) ?: "",
                        size = it.getLong(sizeColumn)
                    )
                )
            }
        }
        return audioList
    }
}