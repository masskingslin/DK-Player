package com.dk.tvplayer.cast

import android.content.Context
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CastDeviceInfo(
    val id: String,
    val name: String,
    val modelName: String,
    val ipAddress: String,
    val isConnected: Boolean,
    val volumeLevel: Int,
    val isMuted: Boolean
)

data class CastState(
    val availableDevices: List<CastDeviceInfo> = emptyList(),
    val connectedDevice: CastDeviceInfo? = null,
    val isDiscovering: Boolean = false,
    val connectionError: String? = null
)

/**
 * Manages Cast device discovery and playback using the public MediaRouter + Cast
 * Framework APIs. Device discovery is handled via [MediaRouter] (the internal
 * `DiscoveryManager` class used previously is not part of the public Cast SDK).
 */
class EnhancedCastManager(
    private val context: Context,
    private val castContext: CastContext
) : SessionManagerListener<CastSession> {

    private val _castState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = _castState

    private val sessionManager = castContext.sessionManager
    private val mediaRouter = MediaRouter.getInstance(context)
    private val routeSelector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
        .build()

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDeviceList()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDeviceList()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDeviceList()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refreshDeviceList()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refreshDeviceList()
    }

    init {
        sessionManager.addSessionManagerListener(this, CastSession::class.java)
    }

    fun startDeviceDiscovery() {
        try {
            _castState.value = _castState.value.copy(isDiscovering = true, connectionError = null)
            mediaRouter.addCallback(
                routeSelector,
                routerCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
            refreshDeviceList()
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                isDiscovering = false,
                connectionError = "Discovery failed: ${e.message}"
            )
        }
    }

    fun stopDeviceDiscovery() {
        try {
            mediaRouter.removeCallback(routerCallback)
            _castState.value = _castState.value.copy(isDiscovering = false)
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                connectionError = "Stop discovery failed: ${e.message}"
            )
        }
    }

    fun connectToDevice(deviceId: String) {
        try {
            val route = mediaRouter.routes.find { it.id == deviceId }
            if (route != null) {
                mediaRouter.selectRoute(route)
            }
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                connectionError = "Connection failed: ${e.message}"
            )
        }
    }

    fun disconnectDevice() {
        try {
            sessionManager.endCurrentSession(true)
            _castState.value = _castState.value.copy(connectedDevice = null)
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                connectionError = "Disconnection failed: ${e.message}"
            )
        }
    }

    fun setVolume(level: Int) {
        try {
            val castSession = sessionManager.currentCastSession
            if (castSession != null) {
                val clampedLevel = level.coerceIn(0, 15)
                castSession.remoteMediaClient?.setStreamVolume(clampedLevel.toDouble() / 15.0)
            }
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                connectionError = "Volume control failed: ${e.message}"
            )
        }
    }

    fun setMuted(muted: Boolean) {
        try {
            val castSession = sessionManager.currentCastSession
            if (castSession != null) {
                castSession.remoteMediaClient?.setStreamMute(muted)
            }
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                connectionError = "Mute control failed: ${e.message}"
            )
        }
    }

    fun playOnCast(contentUrl: String, contentType: String = "video/mp4", title: String = "Playing") {
        try {
            val castSession = sessionManager.currentCastSession
            if (castSession != null) {
                val remoteMediaClient = castSession.remoteMediaClient
                if (remoteMediaClient != null) {
                    val mediaInfo = buildMediaInfo(contentUrl, contentType, title)
                    remoteMediaClient.load(mediaInfo)
                }
            }
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                connectionError = "Play failed: ${e.message}"
            )
        }
    }

    private fun buildMediaInfo(url: String, contentType: String, title: String): com.google.android.gms.cast.MediaInfo {
        val metadata = com.google.android.gms.cast.MediaMetadata(
            com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE
        ).apply {
            putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, title)
            putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, "Streaming from DK Player")
        }

        return com.google.android.gms.cast.MediaInfo.Builder(url)
            .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .build()
    }

    fun refreshDeviceList() {
        try {
            val selectedRouteId = mediaRouter.selectedRoute.id
            val deviceInfoList = mediaRouter.routes
                .filter { it.matchesSelector(routeSelector) && !it.isDefault }
                .map { route ->
                    CastDeviceInfo(
                        id = route.id,
                        name = route.name,
                        modelName = route.description ?: "",
                        ipAddress = "",
                        isConnected = route.id == selectedRouteId,
                        volumeLevel = route.volume,
                        isMuted = false
                    )
                }
            _castState.value = _castState.value.copy(availableDevices = deviceInfoList)
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                connectionError = "Refresh failed: ${e.message}"
            )
        }
    }

    override fun onSessionStarted(session: CastSession, sessionId: String) {
        val device = session.castDevice
        _castState.value = _castState.value.copy(
            connectedDevice = device?.let {
                CastDeviceInfo(
                    id = it.deviceId,
                    name = it.friendlyName,
                    modelName = it.modelName ?: "",
                    ipAddress = it.ipAddress?.hostAddress ?: "Unknown",
                    isConnected = true,
                    volumeLevel = 50,
                    isMuted = false
                )
            },
            connectionError = null
        )
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
        _castState.value = _castState.value.copy(connectedDevice = null)
    }

    override fun onSessionSuspended(session: CastSession, reason: Int) {
        // Handle session suspension
    }

    override fun onSessionResuming(session: CastSession, sessionId: String) {
        // Handle session resuming
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
        // Handle session resume
    }

    override fun onSessionStarting(session: CastSession) {
        // Handle session starting
    }

    override fun onSessionEnding(session: CastSession) {
        // Handle session ending
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) {
        _castState.value = _castState.value.copy(
            connectionError = "Failed to start session: Error code $error"
        )
    }

    override fun onSessionResumeFailed(session: CastSession, error: Int) {
        _castState.value = _castState.value.copy(
            connectionError = "Failed to resume session: Error code $error"
        )
    }

    fun release() {
        try {
            sessionManager.removeSessionManagerListener(this, CastSession::class.java)
            mediaRouter.removeCallback(routerCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
