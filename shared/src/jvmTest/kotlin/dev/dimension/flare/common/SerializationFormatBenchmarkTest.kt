package dev.dimension.flare.common

import com.fleeksoft.ksoup.nodes.Element
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.system.measureNanoTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock

@OptIn(ExperimentalSerializationApi::class)
class SerializationFormatBenchmarkTest {
    @BeforeTest
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun compareJsonAndProtoBufForStoredTimelinePayload() =
        runTest {
            if (!readBooleanFlag(RUN_BENCHMARKS_PROPERTY, RUN_BENCHMARKS_ENV)) {
                println(
                    "Skipping benchmark. Set -D$RUN_BENCHMARKS_PROPERTY=true or $RUN_BENCHMARKS_ENV=true to run it.",
                )
                return@runTest
            }

            val warmupIterations = readIntFlag(WARMUP_ITERATIONS_PROPERTY, WARMUP_ITERATIONS_ENV) ?: 1_000
            val benchmarkIterations = readIntFlag(BENCHMARK_ITERATIONS_PROPERTY, BENCHMARK_ITERATIONS_ENV) ?: 5_000
            val payload = createStoredPayload()
            val serializer = UiTimelineV2.serializer()

            val jsonPayload = JSON.encodeToString(serializer, payload)
            val protoPayload = ProtoBuf.encodeToByteArray(serializer, payload)

            verifyDecode(payload, JSON.decodeFromString(serializer, jsonPayload))
            verifyDecode(payload, ProtoBuf.decodeFromByteArray(serializer, protoPayload))

            val jsonEncode = benchmarkStringEncoding(payload, serializer, warmupIterations, benchmarkIterations)
            val jsonDecode = benchmarkStringDecoding(jsonPayload, serializer, warmupIterations, benchmarkIterations)
            val protoEncode = benchmarkBytesEncoding(payload, serializer, warmupIterations, benchmarkIterations)
            val protoDecode = benchmarkBytesDecoding(protoPayload, serializer, warmupIterations, benchmarkIterations)

            println(
                buildString {
                    appendLine("Serialization benchmark")
                    appendLine("Payload type: ${payload.itemType}")
                    appendLine("Warmup iterations: $warmupIterations")
                    appendLine("Benchmark iterations: $benchmarkIterations")
                    appendLine("JSON size: ${jsonPayload.encodeToByteArray().size} bytes")
                    appendLine("ProtoBuf size: ${protoPayload.size} bytes")
                    appendLine("JSON encode avg: ${jsonEncode.averageMicros.formatMicros()} us")
                    appendLine("JSON decode avg: ${jsonDecode.averageMicros.formatMicros()} us")
                    appendLine("ProtoBuf encode avg: ${protoEncode.averageMicros.formatMicros()} us")
                    appendLine("ProtoBuf decode avg: ${protoDecode.averageMicros.formatMicros()} us")
                    appendLine(
                        "Checksums: json=${jsonEncode.checksum}/${jsonDecode.checksum}, proto=${protoEncode.checksum}/${protoDecode.checksum}",
                    )
                },
            )
        }

    private fun benchmarkStringEncoding(
        payload: UiTimelineV2,
        serializer: kotlinx.serialization.KSerializer<UiTimelineV2>,
        warmupIterations: Int,
        benchmarkIterations: Int,
    ): BenchmarkResult {
        var checksum = 0L
        repeat(warmupIterations) {
            val value = JSON.encodeToString(serializer, payload)
            checksum += value.length.toLong()
        }
        val totalNanos =
            measureNanoTime {
                repeat(benchmarkIterations) {
                    val value = JSON.encodeToString(serializer, payload)
                    checksum = checksum * 31 + value.length
                }
            }
        return BenchmarkResult(totalNanos = totalNanos, iterations = benchmarkIterations, checksum = checksum)
    }

    private fun benchmarkStringDecoding(
        payload: String,
        serializer: kotlinx.serialization.KSerializer<UiTimelineV2>,
        warmupIterations: Int,
        benchmarkIterations: Int,
    ): BenchmarkResult {
        var checksum = 0L
        repeat(warmupIterations) {
            val value = JSON.decodeFromString(serializer, payload)
            checksum += value.statusKey.hashCode().toLong()
        }
        val totalNanos =
            measureNanoTime {
                repeat(benchmarkIterations) {
                    val value = JSON.decodeFromString(serializer, payload)
                    checksum = checksum * 31 + value.statusKey.hashCode()
                }
            }
        return BenchmarkResult(totalNanos = totalNanos, iterations = benchmarkIterations, checksum = checksum)
    }

    private fun benchmarkBytesEncoding(
        payload: UiTimelineV2,
        serializer: kotlinx.serialization.KSerializer<UiTimelineV2>,
        warmupIterations: Int,
        benchmarkIterations: Int,
    ): BenchmarkResult {
        var checksum = 0L
        repeat(warmupIterations) {
            val value = ProtoBuf.encodeToByteArray(serializer, payload)
            checksum += value.size.toLong()
        }
        val totalNanos =
            measureNanoTime {
                repeat(benchmarkIterations) {
                    val value = ProtoBuf.encodeToByteArray(serializer, payload)
                    checksum = checksum * 31 + value.size
                }
            }
        return BenchmarkResult(totalNanos = totalNanos, iterations = benchmarkIterations, checksum = checksum)
    }

    private fun benchmarkBytesDecoding(
        payload: ByteArray,
        serializer: kotlinx.serialization.KSerializer<UiTimelineV2>,
        warmupIterations: Int,
        benchmarkIterations: Int,
    ): BenchmarkResult {
        var checksum = 0L
        repeat(warmupIterations) {
            val value = ProtoBuf.decodeFromByteArray(serializer, payload)
            checksum += value.statusKey.hashCode().toLong()
        }
        val totalNanos =
            measureNanoTime {
                repeat(benchmarkIterations) {
                    val value = ProtoBuf.decodeFromByteArray(serializer, payload)
                    checksum = checksum * 31 + value.statusKey.hashCode()
                }
            }
        return BenchmarkResult(totalNanos = totalNanos, iterations = benchmarkIterations, checksum = checksum)
    }

    private fun verifyDecode(
        expected: UiTimelineV2,
        actual: UiTimelineV2,
    ) {
        assertEquals(expected.itemType, actual.itemType)
        assertEquals(expected.statusKey, actual.statusKey)
        val expectedPost = assertIs<UiTimelineV2.Post>(expected)
        val actualPost = assertIs<UiTimelineV2.Post>(actual)
        assertEquals(expectedPost.content.raw, actualPost.content.raw)
        assertEquals(expectedPost.actions.size, actualPost.actions.size)
        assertEquals(expectedPost.images.size, actualPost.images.size)
        assertEquals(expectedPost.references.size, actualPost.references.size)
        assertEquals(expectedPost.emojiReactions.size, actualPost.emojiReactions.size)
        assertEquals(expectedPost.poll?.options?.size, actualPost.poll?.options?.size)
    }

    private suspend fun createStoredPayload(): UiTimelineV2 {
        val accountKey = MicroBlogKey(id = "benchmark-account", host = "bench.example")
        val rootUser = createUser(MicroBlogKey(id = "root-user", host = "bench.example"), "Root User")
        val quoteUser = createUser(MicroBlogKey(id = "quote-user", host = "bench.example"), "Quote User")
        val parentUser = createUser(MicroBlogKey(id = "parent-user", host = "bench.example"), "Parent User")
        val repostUser = createUser(MicroBlogKey(id = "repost-user", host = "bench.example"), "Repost User")

        val quote =
            createPost(
                accountKey = accountKey,
                user = quoteUser,
                statusKey = MicroBlogKey(id = "quote-status", host = "bench.example"),
                text = "Quoted content with enough text to resemble a real payload.",
                mediaCount = 2,
            )
        val parent =
            createPost(
                accountKey = accountKey,
                user = parentUser,
                statusKey = MicroBlogKey(id = "parent-status", host = "bench.example"),
                text = "Parent content that becomes a stored reference after sanitization.",
                mediaCount = 1,
            )
        val repost =
            createPost(
                accountKey = accountKey,
                user = repostUser,
                statusKey = MicroBlogKey(id = "repost-status", host = "bench.example"),
                text = "Reposted content with a card and poll.",
                mediaCount = 3,
            )

        val root =
            createPost(
                accountKey = accountKey,
                user = rootUser,
                statusKey = MicroBlogKey(id = "root-status", host = "bench.example"),
                text =
                    "Root content with multiple attachments, actions, reactions, and a poll to approximate a stored timeline entry.",
                quote = listOf(quote),
                parents = listOf(parent),
                internalRepost = repost,
                mediaCount = 4,
            )

        val stored =
            TimelinePagingMapper
                .toDb(root, pagingKey = "benchmark")
                .status
                .status
                .data
                .content
        val storedPost = assertIs<UiTimelineV2.Post>(stored)
        assertEquals(0, storedPost.parents.size)
        assertEquals(0, storedPost.quote.size)
        assertEquals(null, storedPost.internalRepost)
        return stored
    }

    private fun createUser(
        key: MicroBlogKey,
        name: String,
    ): UiProfile =
        UiProfile(
            key = key,
            handle =
                UiHandle(
                    raw = key.id,
                    host = key.host,
                ),
            avatar = "https://${key.host}/${key.id}.png",
            nameInternal = name.toUiPlainText(),
            platformType = PlatformType.Mastodon,
            clickEvent = ClickEvent.Noop,
            banner = "https://${key.host}/${key.id}/banner.png",
            description =
                Element("p")
                    .apply {
                        appendText("Profile description for $name with links and some additional text.")
                    }.toUi(),
            matrices =
                UiProfile.Matrices(
                    fansCount = 1234,
                    followsCount = 567,
                    statusesCount = 8901,
                    platformFansCount = "1.2K",
                ),
            mark = persistentListOf(UiProfile.Mark.Verified, UiProfile.Mark.Bot),
            bottomContent =
                UiProfile.BottomContent.Fields(
                    fields =
                        mapOf(
                            "Website" to "https://${key.host}".toUiPlainText(),
                            "Location" to "Benchmark City".toUiPlainText(),
                        ).let {
                            persistentMapOf(
                                "Website" to it.getValue("Website"),
                                "Location" to it.getValue("Location"),
                            )
                        },
                ),
        )

    private fun createPost(
        accountKey: MicroBlogKey,
        user: UiProfile,
        statusKey: MicroBlogKey,
        text: String,
        quote: List<UiTimelineV2.Post> = emptyList(),
        parents: List<UiTimelineV2.Post> = emptyList(),
        internalRepost: UiTimelineV2.Post? = null,
        mediaCount: Int,
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            message = null,
            platformType = PlatformType.Mastodon,
            images =
                List(mediaCount) { index ->
                    UiMedia.Image(
                        url = "https://${statusKey.host}/${statusKey.id}/media-$index.jpg",
                        previewUrl = "https://${statusKey.host}/${statusKey.id}/media-$index-preview.jpg",
                        description = "image-$index",
                        height = 1080f,
                        width = 1920f,
                        sensitive = index % 2 == 0,
                    )
                }.toPersistentList(),
            sensitive = false,
            contentWarning = "cw-$text".toUiPlainText(),
            user = user,
            quote = quote.toPersistentList(),
            content = text.toUiPlainText(),
            actions = createActions(statusKey),
            poll = createPoll(statusKey, accountKey),
            statusKey = statusKey,
            card =
                UiCard(
                    title = "Card for ${statusKey.id}",
                    description = "Card description for ${statusKey.id}",
                    media =
                        UiMedia.Image(
                            url = "https://${statusKey.host}/${statusKey.id}/card.jpg",
                            previewUrl = "https://${statusKey.host}/${statusKey.id}/card-preview.jpg",
                            description = "card",
                            height = 630f,
                            width = 1200f,
                            sensitive = false,
                        ),
                    url = "https://${statusKey.host}/${statusKey.id}",
                ),
            createdAt = Clock.System.now().toUi(),
            emojiReactions =
                listOf(
                    UiTimelineV2.Post.EmojiReaction(
                        name = ":flare:",
                        url = "https://${statusKey.host}/emoji/flare.png",
                        count = UiNumber(10),
                        clickEvent = ClickEvent.Noop,
                        isUnicode = false,
                        me = true,
                    ),
                    UiTimelineV2.Post.EmojiReaction(
                        name = "🔥",
                        url = "",
                        count = UiNumber(5),
                        clickEvent = ClickEvent.Noop,
                        isUnicode = true,
                        me = false,
                    ),
                ).toPersistentList(),
            sourceChannel = UiTimelineV2.Post.SourceChannel(id = "channel-${statusKey.id}", name = "Benchmark"),
            visibility = UiTimelineV2.Post.Visibility.Public,
            replyToHandle = "@reply@example.com",
            references = persistentListOf(),
            parents = parents.toPersistentList(),
            internalRepost = internalRepost,
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(accountKey),
        )

    private fun createActions(statusKey: MicroBlogKey) =
        persistentListOf(
            ActionMenu.Item(
                updateKey = "reply-${statusKey.id}",
                icon = UiIcon.Reply,
                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                count = UiNumber(100),
            ),
            ActionMenu.Group(
                displayItem =
                    ActionMenu.Item(
                        updateKey = "like-${statusKey.id}",
                        icon = UiIcon.Like,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Like),
                        count = UiNumber(200),
                    ),
                actions =
                    persistentListOf(
                        ActionMenu.Item(
                            updateKey = "bookmark-${statusKey.id}",
                            icon = UiIcon.Bookmark,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Bookmark),
                        ),
                        ActionMenu.Item(
                            updateKey = "share-${statusKey.id}",
                            icon = UiIcon.Share,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                        ),
                    ),
            ),
        )

    private fun createPoll(
        statusKey: MicroBlogKey,
        accountKey: MicroBlogKey,
    ) = UiPoll(
        id = "poll-${statusKey.id}",
        options =
            listOf(
                UiPoll.Option(title = "One", votesCount = 10, percentage = 0.2f),
                UiPoll.Option(title = "Two", votesCount = 20, percentage = 0.4f),
                UiPoll.Option(title = "Three", votesCount = 20, percentage = 0.4f),
            ).toPersistentList(),
        multiple = true,
        ownVotes = persistentListOf(1),
        voteMutation =
            dev.dimension.flare.data.datasource.microblog.StatusMutation(
                statusKey = statusKey,
                accountKey = accountKey,
                type = dev.dimension.flare.data.datasource.microblog.StatusMutation.TYPE_VOTE,
                params = mapOf(
                    dev.dimension.flare.data.datasource.microblog.StatusMutation.PARAM_POLL_ID to "vote-${statusKey.id}",
                    dev.dimension.flare.data.datasource.microblog.StatusMutation.PARAM_OPTIONS to "1",
                ),
            ),
        expiresAt = Clock.System.now(),
    )

    private fun Double.formatMicros(): String = "%.2f".format(this)

    private fun readBooleanFlag(
        propertyName: String,
        envName: String,
    ): Boolean = System.getProperty(propertyName) == "true" || System.getenv(envName) == "true"

    private fun readIntFlag(
        propertyName: String,
        envName: String,
    ): Int? = System.getProperty(propertyName)?.toIntOrNull() ?: System.getenv(envName)?.toIntOrNull()

    private data class BenchmarkResult(
        val totalNanos: Long,
        val iterations: Int,
        val checksum: Long,
    ) {
        val averageMicros: Double
            get() = totalNanos.toDouble() / iterations.toDouble() / 1_000.0
    }

    private companion object {
        const val RUN_BENCHMARKS_PROPERTY = "flare.runBenchmarks"
        const val WARMUP_ITERATIONS_PROPERTY = "flare.serializationBenchmark.warmup"
        const val BENCHMARK_ITERATIONS_PROPERTY = "flare.serializationBenchmark.iterations"
        const val RUN_BENCHMARKS_ENV = "FLARE_RUN_BENCHMARKS"
        const val WARMUP_ITERATIONS_ENV = "FLARE_SERIALIZATION_BENCHMARK_WARMUP"
        const val BENCHMARK_ITERATIONS_ENV = "FLARE_SERIALIZATION_BENCHMARK_ITERATIONS"
    }
}
