package com.dk.tvplayer.cast

import androidx.media3.common.C
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.discovery.DiscoveryManager
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

class EnhancedCastManager(private val castContext: CastContext) : SessionManagerListener<CastSession> {
    
    private val _castState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = _castState

    private val sessionManager = castContext.sessionManager
    private val discoveryManager = DiscoveryManager.getInstance()

    init {
        sessionManager.addSessionManagerListener(this)
        startDeviceDiscovery()
    }

    fun startDeviceDiscovery() {
        try {
            _castState.value = _castState.value.copy(isDiscovering = true)
            discoveryManager.startDiscoveryManager()
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                isDiscovering = false,
                connectionError = "Discovery failed: ${e.message}"
            )
        }
    }

    fun stopDeviceDiscovery() {
        try {
            discoveryManager.stopDiscoveryManager()
            _castState.value = _castState.value.copy(isDiscovering = false)
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                connectionError = "Stop discovery failed: ${e.message}"
            )
        }
    }

    fun connectToDevice(deviceId: String) {
        try {
            val device = discoveryManager.deviceList.find { it.deviceId == deviceId }
            if (device != null) {
                sessionManager.selectCastDevice(device)
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
            val devices = discoveryManager.deviceList
            val deviceInfoList = devices.map { device ->
                CastDeviceInfo(
                    id = device.deviceId,
                    name = device.friendlyName,
                    modelName = device.modelName,
                    ipAddress = device.ipAddress.hostAddress ?: "Unknown",
                    isConnected = sessionManager.currentCastSession?.castDevice?.deviceId == device.deviceId,
                    volumeLevel = (sessionManager.currentCastSession?.remoteMediaClient?.streamVolume?.toInt() ?: 0) * 15,
                    isMuted = sessionManager.currentCastSession?.remoteMediaClient?.isMediaLoaded ?: false
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
            connectedDevice = CastDeviceInfo(
                id = device.deviceId,
                name = device.friendlyName,
                modelName = device.modelName,
                ipAddress = device.ipAddress.hostAddress ?: "Unknown",
                isConnected = true,
                volumeLevel = 50,
                isMuted = false
            ),
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

    override fun onSessionResume(session: CastSession, wasSuspended: Boolean) {
        // Handle session resume
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) {
        _castState.value = _castState.value.copy(
            connectionError = "Failed to start session: Error code $error"
        )
    }

    fun release() {
        try {
            sessionManager.removeSessionManagerListener(this)
            discoveryManager.stopDiscoveryManager()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
