package com.dk.tvplayer.util

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import com.dk.tvplayer.data.local.LocalVideoItem

sealed interface DeleteVideoResult {
    data object Success : DeleteVideoResult
    /** Android 10+: the app doesn't own this file, so the OS requires explicit,
     *  one-time user consent via this IntentSender before the delete can proceed. */
    data class NeedsPermission(val intentSender: IntentSender) : DeleteVideoResult
    data class Failure(val message: String) : DeleteVideoResult
}

/**
 * Deletes a video found via MediaStore (see LocalVideoScanner) the correct way for
 * scoped storage: going through ContentResolver rather than plain File.delete(), which
 * silently no-ops on modern Android for files the app didn't create itself.
 */
object LocalVideoDeleter {

    fun delete(context: Context, video: LocalVideoItem): DeleteVideoResult {
        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) DeleteVideoResult.Success
            else DeleteVideoResult.Failure("File not found or already removed")
        } catch (e: SecurityException) {
            // On Android 10+, deleting media the app doesn't own throws this instead of
            // just failing — it carries an IntentSender the caller can launch to ask
            // the user for one-time consent, after which the same delete() call (retried
            // from the launcher's result callback) will succeed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                DeleteVideoResult.NeedsPermission(e.userAction.actionIntent.intentSender)
            } else {
                DeleteVideoResult.Failure(e.message ?: "Permission denied")
            }
        } catch (e: Exception) {
            DeleteVideoResult.Failure(e.message ?: "Unknown error")
        }
    }
}
