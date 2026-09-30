@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dk.tvplayer.remote

import android.content.Context
import android.media.AudioManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.webkit.MimeTypeMap
import com.dk.tvplayer.data.local.LocalAudioScanner
import com.dk.tvplayer.data.local.LocalVideoScanner
import com.dk.tvplayer.data.local.TvDatabase
import com.dk.tvplayer.player.TvExoPlayerManager
import com.dk.tvplayer.util.CrashLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.math.BigInteger
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.security.auth.x500.X500Principal

/**
 * A small HTTPS server that lets a browser on the same network browse the media library,
 * fetch files and control playback — VLC's "Remote access", for DK Player.
 *
 *  - Encryption: TLS only, with a self-signed certificate whose key lives in the Android
 *    Keystore. There is deliberately no plain-HTTP fallback: if TLS can't be set up the
 *    server refuses to start.
 *  - Authentication: a 6-digit one-time code shown on the phone. A wrong code 5 times locks
 *    that address out for a minute and issues a fresh code; a correct code is used up.
 *  - Access: every feature is switchable in [RemoteAccessConfig], and file access is confined
 *    to shared storage.
 */
class RemoteAccessServer(
    private val context: Context,
    private val playerManager: TvExoPlayerManager
) {
    companion object {
        const val PORT = 8443
        private const val KEY_ALIAS = "dk_remote_access"
        private const val SESSION_TTL_MS = 12L * 60 * 60 * 1000
        private const val MAX_BODY = 64 * 1024
        private const val MAX_FAILURES = 5
        private const val LOCKOUT_MS = 60_000L
    }

    private var serverSocket: SSLServerSocket? = null
    private val pool = Executors.newFixedThreadPool(8)
    @Volatile private var running = false

    private val sessions = ConcurrentHashMap<String, Long>()
    private val failures = ConcurrentHashMap<String, Int>()
    private val lockedUntil = ConcurrentHashMap<String, Long>()
    private val random = SecureRandom()
    @Volatile private var currentOtp = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private val db by lazy { TvDatabase.getDatabase(context) }
    private val storageRoot: File by lazy { Environment.getExternalStorageDirectory().canonicalFile }

    // ---------- lifecycle ----------

    fun start(): Result<Unit> = runCatching {
        val (ssl, fingerprint) = buildSslContext()
            ?: error("Encrypted connections aren't available on this device")
        val ss = ssl.serverSocketFactory.createServerSocket() as SSLServerSocket
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(PORT), 16)
        ss.enabledProtocols = ss.supportedProtocols
            .filter { it == "TLSv1.3" || it == "TLSv1.2" }
            .toTypedArray()
        serverSocket = ss
        running = true
        rotateOtp()
        RemoteAccessState.setFingerprint(fingerprint)
        RemoteAccessState.setAddresses(localAddresses().map { "https://$it:$PORT" })
        Thread({ acceptLoop(ss) }, "dk-remote-accept").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        pool.shutdownNow()
        sessions.clear()
        RemoteAccessState.setSessions(0)
    }

    /** New code, invalidating the old one. */
    fun rotateOtp() {
        currentOtp = String.format("%06d", random.nextInt(1_000_000))
        RemoteAccessState.setOtp(currentOtp)
    }

    fun signOutAll() {
        sessions.clear()
        RemoteAccessState.setSessions(0)
    }

    private fun acceptLoop(ss: SSLServerSocket) {
        while (running) {
            try {
                val socket = ss.accept()
                socket.soTimeout = 15_000
                pool.execute { runCatching { handle(socket) }; runCatching { socket.close() } }
            } catch (t: Throwable) {
                if (!running) return
            }
        }
    }

    // ---------- TLS ----------

    private fun buildSslContext(): Pair<SSLContext, String>? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setDigests(
                    KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512
                )
                .setSignaturePaddings(
                    KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE, KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                    KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
                )
                .setKeySize(2048)
                .setCertificateSubject(X500Principal("CN=DK Player Remote Access"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(Date())
                .setCertificateNotAfter(Date(System.currentTimeMillis() + 10L * 365 * 24 * 3600 * 1000))
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(spec)
                generateKeyPair()
            }
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, null)
        val ssl = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        val der = ks.getCertificate(KEY_ALIAS).encoded
        val fp = MessageDigest.getInstance("SHA-256").digest(der)
            .joinToString(":") { String.format("%02X", it) }
        ssl to fp
    }.getOrNull()

    private fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    // ---------- HTTP plumbing ----------

    private class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: ByteArray
    )

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
    }

    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            if (headers.size > 64) return null
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        val rawTarget = parts[1]
        val path = rawTarget.substringBefore('?')
        val query = HashMap<String, String>()
        rawTarget.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach {
            val k = it.substringBefore('=')
            val v = it.substringAfter('=', "")
            query[URLDecoder.decode(k, "UTF-8")] = URLDecoder.decode(v, "UTF-8")
        }
        return Request(parts[0].uppercase(), path, query, headers, body)
    }

    private fun writeHead(
        out: OutputStream,
        status: Int,
        reason: String,
        headers: Map<String, String>
    ) {
        val sb = StringBuilder("HTTP/1.1 $status $reason\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("Connection: close\r\n")
        sb.append("X-Content-Type-Options: nosniff\r\n")
        sb.append("Cache-Control: no-store\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun sendBytes(
        out: OutputStream, status: Int, reason: String, contentType: String, body: ByteArray,
        extra: Map<String, String> = emptyMap()
    ) {
        writeHead(out, status, reason, mapOf("Content-Type" to contentType, "Content-Length" to body.size.toString()) + extra)
        out.write(body)
        out.flush()
    }

    private fun sendJson(out: OutputStream, status: Int, json: Any, extra: Map<String, String> = emptyMap()) {
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
            404 -> "Not Found"; 429 -> "Too Many Requests"; else -> "Error"
        }
        sendBytes(out, status, reason, "application/json; charset=utf-8", json.toString().toByteArray(), extra)
    }

    private fun sendError(out: OutputStream, status: Int, message: String) =
        sendJson(out, status, JSONObject().put("error", message))

    // ---------- routing ----------

    private fun handle(socket: java.net.Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val out = BufferedOutputStream(socket.getOutputStream())
        val req = readRequest(input) ?: return
        val ip = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: "?"

        if (req.method == "GET" && (req.path == "/" || req.path == "/index.html")) {
            val html = context.assets.open("remote/index.html").use { it.readBytes() }
            sendBytes(
                out, 200, "OK", "text/html; charset=utf-8", html,
                mapOf("Content-Security-Policy" to
                    "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'")
            )
            return
        }
        if (req.method == "POST" && req.path == "/api/login") {
            login(req, ip, out)
            return
        }

        val token = sessionToken(req)
        if (token == null) {
            sendError(out, 401, "Sign in required")
            return
        }

        // State-changing calls must be JSON POSTs (a cross-site form can't send that).
        if (req.method == "POST" && req.headers["content-type"]?.startsWith("application/json") != true) {
            sendError(out, 400, "Expected JSON")
            return
        }

        try {
            when (req.path) {
                "/api/logout" -> {
                    sessions.remove(token)
                    RemoteAccessState.setSessions(sessions.size)
                    sendJson(out, 200, JSONObject().put("ok", true),
                        mapOf("Set-Cookie" to "dk_session=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0"))
                }
                "/api/config" -> sendJson(out, 200, configJson())
                "/api/status" -> sendJson(out, 200, statusJson())
                "/api/control" -> control(req, out)
                "/api/library/videos" -> requireAllowed(out, RemoteContent.VIDEO) { sendJson(out, 200, videosJson()) }
                "/api/library/audio" -> requireAllowed(out, RemoteContent.AUDIO) { sendJson(out, 200, audioJson()) }
                "/api/library/playlists" -> requireAllowed(out, RemoteContent.PLAYLISTS) { sendJson(out, 200, playlistsJson()) }
                "/api/search" -> requireAllowed(out, RemoteContent.SEARCH) { sendJson(out, 200, searchJson(req.query["q"].orEmpty())) }
                "/api/history" -> requireAllowed(out, RemoteContent.HISTORY) { sendJson(out, 200, historyJson()) }
                "/api/files" -> requireAllowed(out, RemoteContent.FILE_BROWSER) { listFiles(req, out) }
                "/api/file" -> requireAllowed(out, RemoteContent.FILE_BROWSER) { sendFile(req, out) }
                "/api/logs" -> requireAllowed(out, RemoteContent.LOGS) { sendLogs(out) }
                else -> sendError(out, 404, "Not found")
            }
        } catch (t: Throwable) {
            runCatching { sendError(out, 500, "Server error") }
        }
    }

    private fun requireAllowed(out: OutputStream, item: RemoteContent, block: () -> Unit) {
        if (!RemoteAccessConfig.isAllowed(item)) sendError(out, 403, "${item.label} access is turned off on the device")
        else block()
    }

    // ---------- auth ----------

    private fun sessionToken(req: Request): String? {
        val cookie = req.headers["cookie"] ?: return null
        val token = cookie.split(';').map { it.trim() }
            .firstOrNull { it.startsWith("dk_session=") }?.substringAfter('=') ?: return null
        val expiry = sessions[token] ?: return null
        if (System.currentTimeMillis() > expiry) {
            sessions.remove(token)
            RemoteAccessState.setSessions(sessions.size)
            return null
        }
        return token
    }

    private fun login(req: Request, ip: String, out: OutputStream) {
        val now = System.currentTimeMillis()
        val lockedFor = (lockedUntil[ip] ?: 0L) - now
        if (lockedFor > 0) {
            sendError(out, 429, "Too many attempts. Try again in ${lockedFor / 1000 + 1}s")
            return
        }
        val code = runCatching { JSONObject(String(req.body)).optString("code") }.getOrDefault("")
        val ok = code.length == 6 && MessageDigest.isEqual(code.toByteArray(), currentOtp.toByteArray())
        if (ok) {
            failures.remove(ip)
            val bytes = ByteArray(32).also { random.nextBytes(it) }
            val token = bytes.joinToString("") { String.format("%02x", it) }
            sessions[token] = now + SESSION_TTL_MS
            RemoteAccessState.setSessions(sessions.size)
            rotateOtp() // the code is single-use
            sendJson(out, 200, JSONObject().put("ok", true),
                mapOf("Set-Cookie" to "dk_session=$token; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${SESSION_TTL_MS / 1000}"))
        } else {
            val n = (failures[ip] ?: 0) + 1
            if (n >= MAX_FAILURES) {
                failures.remove(ip)
                lockedUntil[ip] = now + LOCKOUT_MS
                rotateOtp()
                sendError(out, 429, "Too many attempts. A new code was generated")
            } else {
                failures[ip] = n
                sendError(out, 401, "Wrong code (${MAX_FAILURES - n} tries left)")
            }
        }
    }

    // ---------- JSON builders ----------

    private fun configJson(): JSONObject {
        val o = JSONObject()
        RemoteContent.values().forEach { o.put(it.key, RemoteAccessConfig.isAllowed(it)) }
        return o
    }

    private fun volumePercent(): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    private fun statusJson(): JSONObject = JSONObject()
        .put("title", playerManager.currentMediaTitle ?: "")
        .put("playing", playerManager.isPlayingFlow.value)
        .put("positionMs", playerManager.currentPositionFlow.value)
        .put("durationMs", playerManager.durationFlow.value)
        .put("speed", playerManager.playbackSpeedFlow.value.toDouble())
        .put("volume", volumePercent())

    private fun videosJson(): JSONArray {
        val arr = JSONArray()
        LocalVideoScanner(context).scanDeviceVideos().forEach {
            arr.put(JSONObject().put("name", it.name).put("path", it.filePath)
                .put("durationMs", it.duration).put("size", it.size))
        }
        return arr
    }

    private fun audioJson(): JSONArray {
        val arr = JSONArray()
        LocalAudioScanner(context).scanDeviceAudio().forEach {
            arr.put(JSONObject().put("name", it.title).put("artist", it.artist).put("album", it.album)
                .put("path", it.filePath).put("durationMs", it.duration).put("size", it.size))
        }
        return arr
    }

    private fun playlistsJson(): JSONArray = runBlocking {
        val arr = JSONArray()
        db.playlistDao().getAllPlaylistsOnce().forEach { p ->
            val items = JSONArray()
            db.playlistDao().getItemsForPlaylistOnce(p.id).forEach {
                items.put(JSONObject().put("title", it.title).put("url", it.mediaUrl))
            }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("items", items))
        }
        arr
    }

    private fun searchJson(query: String): JSONObject {
        val q = query.trim().lowercase()
        val result = JSONObject().put("videos", JSONArray()).put("audio", JSONArray())
        if (q.length < 2) return result
        if (RemoteAccessConfig.isAllowed(RemoteContent.VIDEO)) {
            val all = videosJson()
            val arr = JSONArray()
            for (i in 0 until all.length()) {
                val o = all.getJSONObject(i)
                if (o.getString("name").lowercase().contains(q)) arr.put(o)
            }
            result.put("videos", arr)
        }
        if (RemoteAccessConfig.isAllowed(RemoteContent.AUDIO)) {
            val all = audioJson()
            val arr = JSONArray()
            for (i in 0 until all.length()) {
                val o = all.getJSONObject(i)
                if ((o.getString("name") + " " + o.getString("artist")).lowercase().contains(q)) arr.put(o)
            }
            result.put("audio", arr)
        }
        return result
    }

    private fun historyJson(): JSONArray = runBlocking {
        val arr = JSONArray()
        db.historyDao().getRecentHistory().first().forEach {
            arr.put(JSONObject().put("title", it.title).put("url", it.mediaUrl)
                .put("positionMs", it.lastPositionMs).put("durationMs", it.durationMs)
                .put("watchedAt", it.lastWatchedTimestamp))
        }
        arr
    }

    // ---------- files ----------

    /** Resolves [path] and only returns it if it is inside shared storage. */
    private fun resolveInStorage(path: String?): File? {
        val f = runCatching {
            if (path.isNullOrBlank()) storageRoot else File(path).canonicalFile
        }.getOrNull() ?: return null
        return f.takeIf { it == storageRoot || it.path.startsWith(storageRoot.path + File.separator) }
    }

    private fun listFiles(req: Request, out: OutputStream) {
        val dir = resolveInStorage(req.query["path"])
        if (dir == null || !dir.isDirectory) {
            sendError(out, 404, "Folder not found")
            return
        }
        val entries = JSONArray()
        (dir.listFiles() ?: emptyArray())
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .forEach {
                entries.put(JSONObject().put("name", it.name).put("isDir", it.isDirectory)
                    .put("size", if (it.isFile) it.length() else 0L)
                    .put("modified", it.lastModified()).put("path", it.path))
            }
        sendJson(out, 200, JSONObject()
            .put("path", dir.path)
            .put("parent", if (dir == storageRoot) JSONObject.NULL else dir.parentFile?.path ?: JSONObject.NULL)
            .put("entries", entries))
    }

    private fun sendFile(req: Request, out: OutputStream) {
        val file = resolveInStorage(req.query["path"])
        if (file == null || !file.isFile) {
            sendError(out, 404, "File not found")
            return
        }
        val length = file.length()
        var start = 0L
        var end = length - 1
        var partial = false
        req.headers["range"]?.takeIf { it.startsWith("bytes=") }?.let { range ->
            val spec = range.removePrefix("bytes=").substringBefore(',').trim()
            val from = spec.substringBefore('-')
            val to = spec.substringAfter('-', "")
            if (from.isEmpty()) {
                val suffix = to.toLongOrNull() ?: return@let
                start = (length - suffix).coerceAtLeast(0)
            } else {
                start = from.toLongOrNull() ?: return@let
                end = to.toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)
            }
            partial = true
        }
        if (start > end || start >= length) {
            writeHead(out, 416, "Range Not Satisfiable", mapOf("Content-Range" to "bytes */$length", "Content-Length" to "0"))
            out.flush()
            return
        }
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val disposition = if (req.query["inline"] == "1") "inline" else "attachment"
        val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val headers = linkedMapOf(
            "Content-Type" to mime,
            "Content-Length" to (end - start + 1).toString(),
            "Accept-Ranges" to "bytes",
            "Content-Disposition" to "$disposition; filename*=UTF-8''$encodedName"
        )
        if (partial) headers["Content-Range"] = "bytes $start-$end/$length"
        writeHead(out, if (partial) 206 else 200, if (partial) "Partial Content" else "OK", headers)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buffer = ByteArray(64 * 1024)
            var remaining = end - start + 1
            while (remaining > 0) {
                val n = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (n < 0) break
                out.write(buffer, 0, n)
                remaining -= n
            }
        }
        out.flush()
    }

    private fun sendLogs(out: OutputStream) {
        val log = CrashLogger.latestLogFile(context)
        if (log == null) sendError(out, 404, "No log files")
        else sendBytes(out, 200, "OK", "text/plain; charset=utf-8", log.readBytes())
    }

    // ---------- playback control ----------

    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask(block)
        mainHandler.post(task)
        return task.get(3, TimeUnit.SECONDS)
    }

    private fun control(req: Request, out: OutputStream) {
        if (!RemoteAccessConfig.isAllowed(RemoteContent.CONTROL)) {
            sendError(out, 403, "Playback control is turned off on the device")
            return
        }
        val body = runCatching { JSONObject(String(req.body)) }.getOrNull()
        if (body == null) {
            sendError(out, 400, "Bad request")
            return
        }
        val error: String? = onMain {
            val player = playerManager.activePlayerFlow.value
            when (body.optString("action")) {
                "toggle" -> { playerManager.togglePlayPause(); null }
                "play" -> { player.play(); null }
                "pause" -> { player.pause(); null }
                "seek" -> { playerManager.seekTo(body.optLong("positionMs", 0L)); null }
                "seekBy" -> {
                    playerManager.seekTo((player.currentPosition + body.optLong("deltaMs", 0L)).coerceAtLeast(0L))
                    null
                }
                "speed" -> {
                    playerManager.setPlaybackSpeed(body.optDouble("value", 1.0).toFloat().coerceIn(0.25f, 4f))
                    null
                }
                "volume" -> {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val level = (body.optInt("percent", 50).coerceIn(0, 100) * max / 100)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
                    null
                }
                "playMedia" -> {
                    val path = body.optString("path")
                    val local = resolveInStorage(path)?.takeIf { it.isFile }
                    when {
                        local != null -> { playerManager.play(local.path, title = local.name); null }
                        isPlaylistUrl(path) -> { playerManager.play(path, title = body.optString("title").ifBlank { path }); null }
                        else -> "That item can't be played remotely"
                    }
                }
                else -> "Unknown action"
            }
        }
        if (error == null) sendJson(out, 200, JSONObject().put("ok", true)) else sendError(out, 400, error)
    }

    /** Streams from playlists can be started remotely, but only URLs the user saved. */
    private fun isPlaylistUrl(url: String): Boolean {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return false
        return runBlocking {
            db.playlistDao().getAllPlaylistsOnce().any { p ->
                db.playlistDao().getItemsForPlaylistOnce(p.id).any { it.mediaUrl == url }
            }
        }
    }
}
