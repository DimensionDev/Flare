package dev.dimension.flare.data.network.bluesky

import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.embed.RecordWithMediaViewMediaUnion
import app.bsky.embed.VideoView
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import dev.dimension.flare.data.network.bluesky.model.DidDoc
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import sh.christian.ozone.BlueskyJson
import kotlin.time.Clock

private val videoDownloadResolver by lazy { BlueskyVideoDownloadResolver() }

internal suspend fun resolveBlueskyVideoDownloadUrls(posts: Collection<PostView>): Map<String, String> =
    videoDownloadResolver.resolve(posts)

/** Resolves video blobs without making the pure UI mappers perform network requests. */
internal class BlueskyVideoDownloadResolver(
    private val client: HttpClient = ktorClient { followRedirects = false },
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()
    private val requests = Semaphore(4)
    private val pdsCache = linkedMapOf<String, CachedPds>()

    suspend fun resolve(posts: Collection<PostView>): Map<String, String> {
        val videos = buildMap { posts.forEach { collectVideos(it) } }
        if (videos.isEmpty()) return emptyMap()
        val authors = videos.values.map { it.did }.distinct()

        // A slow or unavailable PDS directory must not prevent a timeline from loading.
        // Successful resolutions remain usable even if another author's lookup times out.
        withTimeoutOrNull(3_000L) {
            coroutineScope {
                authors.forEach { did ->
                    launch { requests.withPermit { resolvePds(did) } }
                }
            }
        }
        val endpoints =
            mutex.withLock {
                authors.mapNotNull { did -> cachedPds(did)?.let { did to it } }.toMap()
            }
        return videos
            .mapNotNull { (playlist, video) ->
                val pds = endpoints[video.did] ?: return@mapNotNull null
                val url =
                    URLBuilder(pds)
                        .apply {
                            appendPathSegments("xrpc", "com.atproto.sync.getBlob")
                            parameters.append("did", video.did)
                            parameters.append("cid", video.cid)
                        }.buildString()
                playlist to url
            }.toMap()
    }

    private suspend fun resolvePds(did: String) {
        if (mutex.withLock { cachedPds(did) } != null) return
        try {
            val response = client.get(didDocumentUrl(did))
            if (!response.status.isSuccess()) return
            val document = BlueskyJson.decodeFromString<DidDoc>(response.bodyAsText())
            if (document.id != did) return
            val service =
                document.service?.firstOrNull {
                    it.type == "AtprotoPersonalDataServer" &&
                        (it.id == "#atproto_pds" || it.id == "$did#atproto_pds")
                } ?: return
            val endpoint = Url(service.serviceEndpoint ?: return).requireHttpsOrigin("Video PDS")
            mutex.withLock {
                pdsCache[did] = CachedPds(endpoint, nowMillis() + 60 * 60 * 1_000L)
                if (pdsCache.size > 256) {
                    pdsCache.remove(pdsCache.keys.first())
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Download metadata is optional; playback and posts remain available.
        }
    }

    private fun cachedPds(did: String): Url? = pdsCache[did]?.takeIf { it.expiresAt > nowMillis() }?.url

    private data class CachedPds(
        val url: Url,
        val expiresAt: Long,
    )
}

private data class VideoBlob(
    val did: String,
    val cid: String,
)

private fun MutableMap<String, VideoBlob>.collectVideos(post: PostView) {
    val did = post.author.did.did
    when (val embed = post.embed) {
        is PostViewEmbedUnion.VideoView -> {
            addVideo(did, embed.value)
        }

        is PostViewEmbedUnion.RecordView -> {
            collectVideos(embed.value.record)
        }

        is PostViewEmbedUnion.RecordWithMediaView -> {
            collectVideos(did, embed.value.media)
            collectVideos(embed.value.record.record)
        }

        else -> {}
    }
}

private fun MutableMap<String, VideoBlob>.collectVideos(record: RecordViewRecordUnion) {
    if (record !is RecordViewRecordUnion.ViewRecord) return
    val did = record.value.author.did.did
    record.value.embeds.orEmpty().forEach { embed ->
        when (embed) {
            is RecordViewRecordEmbedUnion.VideoView -> {
                addVideo(did, embed.value)
            }

            is RecordViewRecordEmbedUnion.RecordWithMediaView -> {
                collectVideos(did, embed.value.media)
            }

            else -> {}
        }
    }
}

private fun MutableMap<String, VideoBlob>.collectVideos(
    did: String,
    media: RecordWithMediaViewMediaUnion,
) {
    if (media is RecordWithMediaViewMediaUnion.VideoView) addVideo(did, media.value)
}

private fun MutableMap<String, VideoBlob>.addVideo(
    did: String,
    video: VideoView,
) {
    put(video.playlist.uri, VideoBlob(did, video.cid.cid))
}
