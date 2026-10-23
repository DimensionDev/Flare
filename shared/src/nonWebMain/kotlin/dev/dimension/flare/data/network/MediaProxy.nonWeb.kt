package dev.dimension.flare.data.network

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.parseQueryString
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import okio.Buffer
import kotlin.uuid.Uuid

public actual object MediaProxy {
    @Throws(Exception::class)
    public actual suspend fun playbackSource(media: UiMedia): MediaPlaybackSource {
        if (!media.isRemote) return MediaPlaybackSource(media.url, media.url)
        val snapshot = ProxyNetwork.snapshot(media.proxy)
        if (snapshot.connection.mode == ProxyMode.SYSTEM && media.customHeaders.isNullOrEmpty()) {
            return MediaPlaybackSource(media.url, snapshot.key + "|" + media.cacheKey, media.url, snapshot)
        }
        resolveMediaUrl(media.url, media.url)
        val resource = Resource(media.url, media.proxy, media.customHeaders.orEmpty().toMap(), null, snapshot.key)
        val url = register(resource)
        lock.withLock {
            entry(url)?.let {
                it.leases++
                it.failure = null
            }
        }
        return MediaPlaybackSource(url, snapshot.key + "|" + media.cacheKey, media.url, snapshot)
    }

    public actual fun retain(source: MediaPlaybackSource) {
        lock.withLock { entry(source.url)?.let { it.leases++ } }
    }

    public actual fun release(source: MediaPlaybackSource) {
        lock.withLock {
            val root = entry(source.url) ?: return@withLock
            if (--root.leases > 0) return@withLock
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val (token, entry) = iterator.next()
                if (token == root.token || entry.resource.root == root.token) {
                    tokens.remove(entry.resource)
                    iterator.remove()
                }
            }
        }
    }

    private data class Resource(
        val url: String,
        val proxy: ProxyRef,
        val headers: Map<String, String>,
        val root: String?,
        val routeKey: String,
        val opaque: Boolean = false,
    )

    private class Entry(
        val resource: Resource,
        val token: String,
    ) {
        var failure: String? = null
        var leases: Int = 0
    }

    private val lock = MediaRelayLock()
    private val entries = LinkedHashMap<String, Entry>()
    private val tokens = mutableMapOf<Resource, String>()
    private var serverFailure: String? = null
    private val scope = CoroutineScope(SupervisorJob() + PlatformDispatchers.IO)
    private val permits = Semaphore(64)
    private val server by lazy {
        MediaRelayListener().also { listener ->
            scope.launch {
                while (true) {
                    permits.acquire()
                    val connection =
                        try {
                            listener.accept()
                        } catch (error: Exception) {
                            permits.release()
                            lock.withLock {
                                serverFailure = error.message ?: "Media relay stopped"
                                entries.values.forEach { it.failure = serverFailure }
                            }
                            return@launch
                        }
                    launch {
                        try {
                            serve(connection)
                        } finally {
                            connection.close()
                            permits.release()
                        }
                    }
                }
            }
        }
    }

    public actual fun failureMessage(localUrl: String): String? = lock.withLock { entry(localUrl)?.failure }

    private fun entry(localUrl: String): Entry? = entries[localUrl.substringBefore('?').substringAfterLast('/').substringBefore('.')]

    private fun register(resource: Resource): String {
        val suffix = Url(resource.url).encodedPath.substringAfterLast('.').takeIf { it.length <= 8 && it.all(Char::isLetterOrDigit) }
        val port = server.port
        return lock.withLock {
            check(serverFailure == null) { serverFailure.orEmpty() }
            check(resource.root == null || entries[resource.root]?.leases?.let { it > 0 } == true) { "Expired media request" }
            val token = token(resource)
            "http://127.0.0.1:$port/$token${suffix?.let { ".$it" }.orEmpty()}"
        }
    }

    private fun token(resource: Resource): String =
        tokens[resource] ?: Uuid.random().toString().also {
            tokens[resource] = it
            entries[it] = Entry(resource, it)
        }

    private suspend fun serve(connection: MediaRelayConnection) {
        var responseStarted = false
        var root: Entry? = null
        try {
            val raw = readHeaders(connection)
            val lines = raw.split("\r\n")
            val request = lines.first().split(' ')
            require(request.size == 3 && request[0] in listOf("GET", "HEAD")) { "Unsupported method" }
            val selected = lock.withLock { entry(request[1]) } ?: error("Expired media request")
            root = lock.withLock {
                if (selected.resource.root == null) selected else entries[selected.resource.root]
            } ?: error("Expired media request")
            val rootToken = root.token
            check(ProxyNetwork.snapshot(selected.resource.proxy).key == selected.resource.routeKey) { "Proxy configuration changed" }
            val headers =
                lines.drop(1).filter { ':' in it }.associate {
                    it.substringBefore(':').lowercase() to
                        it.substringAfter(':').trim()
                }
            require(headers["host"] == "127.0.0.1:${server.port}") { "Invalid loopback host" }
            val client =
                HttpClient(ProxyRoutingEngine(selected.resource.proxy)) {
                    install(HttpTimeout) {
                        connectTimeoutMillis = 30_000
                        socketTimeoutMillis = 60_000
                    }
                    followRedirects = false
                    useDefaultTransformers = false
                }
            try {
                var target =
                    URLBuilder(selected.resource.url)
                        .apply {
                            val query = parseQueryString(request[1].substringAfter('?', ""))
                            for (name in listOf("_HLS_msn", "_HLS_part", "_HLS_skip")) {
                                query.getAll(name)?.let { values ->
                                    parameters.remove(name)
                                    parameters.appendAll(name, values)
                                }
                            }
                        }.buildString()
                var requestHeaders = selected.resource.headers
                for (redirect in 0..8) {
                    var next: String? = null
                    client
                        .prepareRequest(target) {
                            method = HttpMethod.parse(request[0])
                            requestHeaders.forEach { (name, value) ->
                                if (name.lowercase() !in
                                    setOf("host", "connection", "content-length", "transfer-encoding", "accept-encoding")
                                ) {
                                    this.headers.append(name, value)
                                }
                            }
                            listOf("range", "if-range", "if-none-match", "if-modified-since").forEach { name ->
                                headers[name]?.let { this.headers.append(name, it) }
                            }
                            this.headers.append("Accept-Encoding", "identity")
                        }.execute { response ->
                            val location = response.headers["Location"]
                            if (response.status.value in listOf(301, 302, 303, 307, 308) && location != null) {
                                next = resolveMediaUrl(target, location)
                                if (mediaOrigin(next) != mediaOrigin(target)) {
                                    requestHeaders = requestHeaders.filterKeys { it.lowercase() !in setOf("authorization", "cookie") }
                                }
                                return@execute
                            }
                            require(response.headers["Content-Encoding"].orEmpty().let { it.isBlank() || it.equals("identity", true) }) {
                                "Media server ignored the uncompressed transfer request"
                            }
                            val type =
                                response.headers["Content-Type"]
                                    .orEmpty()
                                    .substringBefore(';')
                                    .lowercase()
                            var playlist =
                                !selected.resource.opaque && (
                                    type in
                                        setOf(
                                            "application/vnd.apple.mpegurl",
                                            "application/x-mpegurl",
                                            "audio/mpegurl",
                                            "audio/x-mpegurl",
                                        ) ||
                                        Url(target).encodedPath.endsWith(".m3u8", true)
                                )
                            if (!selected.resource.opaque && response.status.value in 200..299 &&
                                (
                                    Url(target).encodedPath.endsWith(".mpd", true) ||
                                        Url(target).encodedPath.endsWith(".ism/manifest", true)
                                )
                            ) {
                                throw UnsupportedProxyMedia("This streaming format is unsupported through an app proxy")
                            }
                            if (playlist &&
                                response.status.value == 206
                            ) {
                                throw UnsupportedProxyMedia("Partial playlists are unsupported through an app proxy")
                            }
                            val body = response.bodyAsChannel()
                            val prefix = Buffer()
                            val rangeStartsAtBeginning =
                                headers["range"]?.substringAfter('=')?.substringBefore('-')?.trim().let {
                                    it ==
                                        null ||
                                        it == "0"
                                }
                            if (!selected.resource.opaque && request[0] == "GET" && response.status.value in 200..299 &&
                                rangeStartsAtBeginning
                            ) {
                                val bytes = ByteArray(256)
                                while (prefix.size < bytes.size) {
                                    val count = body.readAvailable(bytes, 0, bytes.size - prefix.size.toInt())
                                    if (count < 0) break
                                    prefix.write(bytes, 0, count)
                                }
                                body.closedCause?.let { throw it }
                                val text = prefix.snapshot().utf8().trimStart()
                                playlist = playlist || text.startsWith("#EXTM3U")
                                val markup = if (text.startsWith("<?xml")) text.substringAfter("?>", "").trimStart() else text
                                if (markup.startsWith("<MPD") || markup.startsWith("<SmoothStreamingMedia")) {
                                    throw UnsupportedProxyMedia("This streaming format is unsupported through an app proxy")
                                }
                                require(!text.startsWith('<') && (!type.startsWith("text/") || playlist || type == "text/vtt")) {
                                    "Target did not return playable media"
                                }
                            }
                            if (response.status.value >=
                                400
                            ) {
                                lock.withLock { entries[rootToken]?.failure = "Target returned HTTP ${response.status.value}" }
                            }
                            if (playlist &&
                                response.status.value == 206
                            ) {
                                throw UnsupportedProxyMedia("Partial playlists are unsupported through an app proxy")
                            }
                            val rewritten =
                                if (playlist && request[0] == "GET" && response.status.value == 200) {
                                    val buffer = prefix
                                    val bytes = ByteArray(16 * 1024)
                                    while (true) {
                                        val count = body.readAvailable(bytes)
                                        if (count < 0) break
                                        buffer.write(bytes, 0, count)
                                        if (buffer.size > 1024 * 1024) throw UnsupportedProxyMedia("Playlist exceeds the supported size")
                                    }
                                    body.closedCause?.let { throw it }
                                    rewriteProxyPlaylist(buffer.readUtf8(), target) { url, opaque ->
                                        register(
                                            Resource(
                                                url,
                                                selected.resource.proxy,
                                                requestHeaders.filterKeys {
                                                    mediaOrigin(url) ==
                                                        mediaOrigin(target) ||
                                                        it.lowercase() !in setOf("authorization", "cookie")
                                                },
                                                rootToken,
                                                selected.resource.routeKey,
                                                opaque = opaque,
                                            ),
                                        )
                                    }.encodeToByteArray()
                                } else {
                                    null
                                }
                            val responseHeaders =
                                response.headers
                                    .entries()
                                    .filter { it.key.lowercase() in RESPONSE_HEADERS }
                                    .associate {
                                        it.key to
                                            it.value.joinToString(", ")
                                    }.toMutableMap()
                            if (rewritten != null) {
                                responseHeaders.keys
                                    .filter {
                                        it.equals(
                                            "content-length",
                                            true,
                                        ) || it.equals("content-encoding", true)
                                    }.forEach(responseHeaders::remove)
                                responseHeaders["Content-Length"] = rewritten.size.toString()
                            }
                            responseStarted = true
                            connection.write(
                                (
                                    "HTTP/1.1 ${response.status.value} ${response.status.description}\r\n" +
                                        responseHeaders.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } +
                                        "Connection: close\r\n\r\n"
                                ).encodeToByteArray(),
                            )
                            if (request[0] != "HEAD") {
                                if (rewritten != null) {
                                    connection.write(rewritten)
                                } else {
                                    if (prefix.size > 0) connection.write(prefix.readByteArray())
                                    val bytes = ByteArray(64 * 1024)
                                    while (true) {
                                        val count = body.readAvailable(bytes)
                                        if (count < 0) break
                                        connection.write(bytes, count)
                                    }
                                    body.closedCause?.let { throw it }
                                }
                            }
                        }
                    if (next == null) return
                    target = next
                }
                error("Too many media redirects")
            } finally {
                client.close()
                client.engine.close()
            }
        } catch (error: Exception) {
            lock.withLock {
                root?.failure = error.message ?: "Media request failed"
            }
            if (!responseStarted) {
                runCatching {
                    connection.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".encodeToByteArray())
                }
            }
            if (error is CancellationException) throw error
        }
    }

    private fun readHeaders(connection: MediaRelayConnection): String {
        val buffer = Buffer()
        val byte = ByteArray(1)
        while (buffer.size < 16 * 1024) {
            check(connection.read(byte) == 1) { "Incomplete media request" }
            buffer.write(byte)
            if (buffer.size >= 4 && buffer[buffer.size - 4] == 13.toByte() && buffer[buffer.size - 3] == 10.toByte() &&
                buffer[buffer.size - 2] == 13.toByte() &&
                buffer[buffer.size - 1] == 10.toByte()
            ) {
                return buffer.readUtf8()
            }
        }
        error("Media request headers are too large")
    }

    private val RESPONSE_HEADERS =
        setOf(
            "content-type",
            "content-length",
            "content-range",
            "accept-ranges",
            "etag",
            "last-modified",
            "cache-control",
            "expires",
            "content-encoding",
        )
}

private fun mediaOrigin(url: String): Triple<String, String, Int> = Url(url).let { Triple(it.protocol.name, it.host, it.port) }

internal fun resolveMediaUrl(
    base: String,
    reference: String,
): String {
    val builder = URLBuilder(base).takeFrom(reference)
    val segments = mutableListOf<String>()
    val path = builder.encodedPath.split('/')
    path.forEachIndexed { index, segment ->
        when (segment) {
            "." -> {
                if (index == path.lastIndex) segments.add("")
            }

            ".." -> {
                if (segments.size > 1) segments.removeAt(segments.lastIndex)
                if (index == path.lastIndex) segments.add("")
            }

            else -> {
                segments.add(segment)
            }
        }
    }
    builder.encodedPath = segments.joinToString("/").ifEmpty { "/" }
    return builder.buildString().also {
        val target = Url(it)
        if (target.protocol.name !in setOf("http", "https") || !target.user.isNullOrEmpty() ||
            !target.password.isNullOrEmpty()
        ) {
            throw UnsupportedProxyMedia("Unsupported media URL scheme")
        }
    }
}

internal fun rewriteProxyPlaylist(
    playlist: String,
    base: String,
    register: (String, Boolean) -> String,
): String {
    if (!playlist.trimStart().startsWith("#EXTM3U")) throw UnsupportedProxyMedia("Invalid HLS playlist")
    var encrypted = false
    return playlist.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("#EXT") &&
            trimmed.substringBefore(':') !in HLS_TAGS
        ) {
            throw UnsupportedProxyMedia("This HLS extension is unsupported through an app proxy")
        }
        if (trimmed.contains("{\$") || trimmed.startsWith("#EXT-X-DEFINE:") || trimmed.startsWith("#EXT-X-CONTENT-STEERING:") ||
            trimmed.contains("X-ASSET-") ||
            trimmed.startsWith("#EXT-X-SESSION-DATA:")
        ) {
            throw UnsupportedProxyMedia("This HLS extension is unsupported through an app proxy")
        }
        if (trimmed.startsWith("#EXT-X-KEY:") || trimmed.startsWith("#EXT-X-SESSION-KEY:")) {
            val method = Regex("(?:[:,])METHOD=([^,]+)").find(trimmed)?.groupValues?.get(1)
            if (method !in setOf("NONE", "AES-128")) throw UnsupportedProxyMedia("Protected media is unsupported through an app proxy")
            val format = Regex("KEYFORMAT=\"([^\"]+)\"").find(trimmed)?.groupValues?.get(1)
            if (format != null && format != "identity") throw UnsupportedProxyMedia("Protected media is unsupported through an app proxy")
            if (trimmed.startsWith("#EXT-X-KEY:")) encrypted = method == "AES-128"
        }
        if (trimmed.startsWith('#')) {
            val uriCount = Regex("(?<![A-Z-])URI\\s*=", RegexOption.IGNORE_CASE).findAll(trimmed).count()
            val quotedCount = Regex("(?<![A-Z-])URI=\"[^\"]+\"").findAll(trimmed).count()
            if (uriCount != quotedCount) throw UnsupportedProxyMedia("Unsupported playlist URI attribute")
        }
        when {
            trimmed.isBlank() -> {
                line
            }

            !trimmed.startsWith('#') -> {
                register(resolveMediaUrl(base, trimmed), encrypted)
            }

            else -> {
                Regex("(?<![A-Z-])URI=\"([^\"]+)\"").replace(line) { match ->
                    // AES keys and ciphertext must not be sniffed or rewritten as text.
                    val opaque =
                        trimmed.startsWith("#EXT-X-KEY:") || trimmed.startsWith("#EXT-X-SESSION-KEY:") ||
                            (encrypted && listOf("#EXT-X-MAP:", "#EXT-X-PART:", "#EXT-X-PRELOAD-HINT:").any(trimmed::startsWith))
                    "URI=\"${register(resolveMediaUrl(base, match.groupValues[1]), opaque)}\""
                }
            }
        }
    }
}

internal class UnsupportedProxyMedia(
    message: String,
) : Exception(message)

internal expect class MediaRelayLock() {
    fun <T> withLock(block: () -> T): T
}

internal expect class MediaRelayListener() {
    val port: Int

    fun accept(): MediaRelayConnection
}

internal interface MediaRelayConnection {
    fun read(bytes: ByteArray): Int

    fun write(
        bytes: ByteArray,
        count: Int = bytes.size,
    )

    fun close()
}

private val HLS_TAGS =
    setOf(
        "#EXTM3U",
        "#EXTINF",
        "#EXT-X-VERSION",
        "#EXT-X-INDEPENDENT-SEGMENTS",
        "#EXT-X-START",
        "#EXT-X-TARGETDURATION",
        "#EXT-X-MEDIA-SEQUENCE",
        "#EXT-X-DISCONTINUITY-SEQUENCE",
        "#EXT-X-ENDLIST",
        "#EXT-X-PLAYLIST-TYPE",
        "#EXT-X-I-FRAMES-ONLY",
        "#EXT-X-BYTERANGE",
        "#EXT-X-DISCONTINUITY",
        "#EXT-X-KEY",
        "#EXT-X-MAP",
        "#EXT-X-PROGRAM-DATE-TIME",
        "#EXT-X-DATERANGE",
        "#EXT-X-GAP",
        "#EXT-X-BITRATE",
        "#EXT-X-MEDIA",
        "#EXT-X-STREAM-INF",
        "#EXT-X-I-FRAME-STREAM-INF",
        "#EXT-X-SESSION-KEY",
        "#EXT-X-SERVER-CONTROL",
        "#EXT-X-PART-INF",
        "#EXT-X-PART",
        "#EXT-X-PRELOAD-HINT",
        "#EXT-X-SKIP",
        "#EXT-X-RENDITION-REPORT",
    )
