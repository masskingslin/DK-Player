package com.dk.tvplayer.data.local

import android.content.Context
import android.provider.MediaStore

data class LocalVideoItem(
    val id: Long,
    val name: String,
    val duration: Long,
    val filePath: String,
    val size: Long
)

class LocalVideoScanner(private val context: Context) {
    fun scanDeviceVideos(): List<LocalVideoItem> {
        val videoList = mutableListOf<LocalVideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE
        )

        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (it.moveToNext()) {
                videoList.add(
                    LocalVideoItem(
                        id = it.getLong(idColumn),
                        name = it.getString(nameColumn) ?: "Untitled Video",
                        duration = it.getLong(durationColumn),
                        filePath = it.getString(dataColumn) ?: "",
                        size = it.getLong(sizeColumn)
                    )
                )
            }
        }
        return videoList
    }
}
