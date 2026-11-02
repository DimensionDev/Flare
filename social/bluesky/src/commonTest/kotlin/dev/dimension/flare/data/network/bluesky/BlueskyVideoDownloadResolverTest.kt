package dev.dimension.flare.data.network.bluesky

import app.bsky.actor.ProfileViewBasic
import app.bsky.embed.RecordView
import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.embed.RecordWithMediaView
import app.bsky.embed.RecordWithMediaViewMediaUnion
import app.bsky.embed.VideoView
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class BlueskyVideoDownloadResolverTest {
    @Test
    fun resolvesQuotedAndAttachedVideosUsingTheirOwnAuthorsAndCachesPds() =
        runTest {
            val requests = mutableListOf<String>()
            val client =
                mockClient { request ->
                    val did = request.url.encodedPath.removePrefix("/")
                    requests += did
                    respond(didDocument(did, "https://${did.substringAfterLast(':')}.pds.example"))
                }
            try {
                val quoted =
                    RecordViewRecord(
                        uri = AtUri("at://did:plc:quoted/app.bsky.feed.post/1"),
                        cid = Cid("quoted-post"),
                        author = author("did:plc:quoted"),
                        value = BlueskyJson.encodeAsJsonContent(buildJsonObject {}),
                        indexedAt = Instant.fromEpochMilliseconds(0),
                        embeds = listOf(RecordViewRecordEmbedUnion.VideoView(video("quoted-video"))),
                    )
                val post =
                    post("did:plc:author").copy(
                        embed =
                            PostViewEmbedUnion.RecordWithMediaView(
                                RecordWithMediaView(
                                    record = RecordView(RecordViewRecordUnion.ViewRecord(quoted)),
                                    media = RecordWithMediaViewMediaUnion.VideoView(video("attached-video")),
                                ),
                            ),
                    )
                val resolver = BlueskyVideoDownloadResolver(client)

                val urls = resolver.resolve(listOf(post))
                assertEquals(setOf("did:plc:author", "did:plc:quoted"), requests.toSet())
                assertBlobUrl(urls.getValue(playlist("attached-video")), "author.pds.example", "did:plc:author", "attached-video")
                assertBlobUrl(urls.getValue(playlist("quoted-video")), "quoted.pds.example", "did:plc:quoted", "quoted-video")
                assertEquals(urls, resolver.resolve(listOf(post)))
                assertEquals(2, requests.size)
            } finally {
                client.close()
            }
        }

    @Test
    fun resolvesDidWebAndRefreshesExpiredPdsAfterMigration() =
        runTest {
            var now = 0L
            var host = "old.pds.example"
            var requests = 0
            val client =
                mockClient { request ->
                    requests++
                    assertEquals("https://author.example/.well-known/did.json", request.url.toString())
                    respond(didDocument("did:web:author.example", "https://$host"))
                }
            try {
                val resolver = BlueskyVideoDownloadResolver(client) { now }
                val posts = listOf(post("did:web:author.example"))
                assertEquals("old.pds.example", Url(resolver.resolve(posts).values.single()).host)
                host = "new.pds.example"
                now = 3_600_001L
                assertEquals("new.pds.example", Url(resolver.resolve(posts).values.single()).host)
                assertEquals(2, requests)
            } finally {
                client.close()
            }
        }

    @Test
    fun ignoresMismatchedDidAndInvalidPdsWithoutBreakingOtherVideos() =
        runTest {
            val client =
                mockClient { request ->
                    when (val did = request.url.encodedPath.removePrefix("/")) {
                        "did:plc:wrong" -> respond(didDocument("did:plc:someoneelse", "https://wrong.example"))
                        "did:plc:invalid" -> respond(didDocument(did, "https://invalid.example/path"))
                        "did:plc:unavailable" -> respond("unavailable", HttpStatusCode.ServiceUnavailable)
                        else -> respond(didDocument(did, "https://valid.example"))
                    }
                }
            try {
                val urls =
                    BlueskyVideoDownloadResolver(client).resolve(
                        listOf("wrong", "invalid", "unavailable", "valid").map { post("did:plc:$it", it) },
                    )
                assertEquals(setOf(playlist("valid")), urls.keys)
            } finally {
                client.close()
            }
        }

    @Test
    fun timeoutKeepsCompletedLookupsAndAllowsRetry() =
        runTest {
            var slow = true
            val client =
                mockClient { request ->
                    val did = request.url.encodedPath.removePrefix("/")
                    if (slow && did == "did:plc:slow") delay(10_000)
                    respond(didDocument(did, "https://pds.example"))
                }
            try {
                val resolver = BlueskyVideoDownloadResolver(client)
                val posts = listOf(post("did:plc:fast", "fast"), post("did:plc:slow", "slow"))

                assertEquals(setOf(playlist("fast")), resolver.resolve(posts).keys)
                slow = false
                assertEquals(2, resolver.resolve(posts).size)
            } finally {
                client.close()
            }
        }

    @Test
    fun postsWithoutVideosDoNotResolveIdentities() =
        runTest {
            val client = mockClient { error("No request expected") }
            try {
                assertTrue(BlueskyVideoDownloadResolver(client).resolve(listOf(post("did:plc:author").copy(embed = null))).isEmpty())
            } finally {
                client.close()
            }
        }

    private fun TestScope.mockClient(handler: MockRequestHandler): HttpClient =
        HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            }
        }

    private fun assertBlobUrl(
        value: String,
        host: String,
        did: String,
        cid: String,
    ) {
        val url = Url(value)
        assertEquals(host, url.host)
        assertEquals("/xrpc/com.atproto.sync.getBlob", url.encodedPath)
        assertEquals(did, url.parameters["did"])
        assertEquals(cid, url.parameters["cid"])
    }

    private fun didDocument(
        did: String,
        endpoint: String,
    ): String = """{"id":"$did","service":[{"id":"$did#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"$endpoint"}]}"""

    private fun post(
        did: String,
        cid: String = "video",
    ): PostView =
        PostView(
            uri = AtUri("at://$did/app.bsky.feed.post/1"),
            cid = Cid("post"),
            author = author(did),
            record = BlueskyJson.encodeAsJsonContent(buildJsonObject {}),
            indexedAt = Instant.fromEpochMilliseconds(0),
            embed = PostViewEmbedUnion.VideoView(video(cid)),
        )

    private fun author(did: String): ProfileViewBasic = ProfileViewBasic(did = Did(did), handle = Handle("author.test"))

    private fun video(cid: String): VideoView = VideoView(cid = Cid(cid), playlist = Uri(playlist(cid)))

    private fun playlist(cid: String): String = "https://video.example/$cid/playlist.m3u8"
}
