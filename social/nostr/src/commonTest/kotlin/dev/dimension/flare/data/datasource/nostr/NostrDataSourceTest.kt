package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.common.SwitchingServiceManager
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.network.nostr.QuartzTestRelays
import dev.dimension.flare.data.network.nostr.nostrTestEvent
import dev.dimension.flare.data.network.nostr.nostrTestSigner
import dev.dimension.flare.data.network.nostr.rustNostrKeyFixtures
import dev.dimension.flare.data.network.nostr.service
import dev.dimension.flare.data.platform.NostrCredential
import dev.dimension.flare.data.platform.NostrSignerCredential
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.model.postEventOrNull
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NostrDataSourceTest {
    private val account = MicroBlogKey(nostrTestSigner().pubKey, "nostr")
    private val credential =
        NostrCredential(
            pubkeyHex = account.id,
            signer =
                NostrSignerCredential.LocalKey(
                    rustNostrKeyFixtures
                        .first()
                        .getValue("nsec")
                        .jsonPrimitive.content,
                ),
        )

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun timelineLoadersPassCursorsAndStopAtEmptyPages() =
        runTest {
            withDataSource { source, network, _ ->
                val old = nostrTestEvent("match old", time = 10)
                val newest = nostrTestEvent("match new", time = 20)
                val mention = nostrTestEvent("mention", time = 30, author = 1, tags = listOf(listOf("p", account.id)))
                network.storedEvents += listOf(old, newest, mention)
                val loaders = listOf(source.homeTimeline(), source.userTimeline(account, false), source.searchStatus("match"))
                for (loader in loaders) {
                    val page = loader.load(20, PagingRequest.Refresh)
                    assertEquals(
                        listOf(newest.id, old.id),
                        page.data.map {
                            it
                                .asTimelinePostItem()
                                ?.post
                                ?.statusKey
                                ?.id
                        },
                    )
                    assertEquals("9", page.nextKey)
                    assertNull(loader.load(20, PagingRequest.Append(assertNotNull(page.nextKey))).nextKey)
                    assertNull(loader.load(20, PagingRequest.Prepend("30")).nextKey)
                }
                val notifications = source.notification(NotificationFilter.Mention).load(20, PagingRequest.Refresh)
                assertEquals(
                    listOf(mention.id),
                    notifications.data.map {
                        it
                            .asTimelinePostItem()
                            ?.post
                            ?.statusKey
                            ?.id
                    },
                )
                assertEquals("29", notifications.nextKey)
                val users = source.searchUser(account.id)
                assertEquals(
                    account,
                    users
                        .load(20, PagingRequest.Refresh)
                        .data
                        .single()
                        .key,
                )
                assertNull(users.load(20, PagingRequest.Append("next")).nextKey)
            }
        }

    @Test
    fun detailLoaderReturnsFocalPostThenThreadAndSupportsProfileRelationLoader() =
        runTest {
            withDataSource { source, network, manager ->
                val root = nostrTestEvent(time = 10)
                val reply = nostrTestEvent(time = 20, tags = listOf(listOf("e", root.id, "", "reply")))
                network.storedEvents += listOf(root, reply)
                val key = MicroBlogKey(root.id, "nostr")
                val context = source.context(key)
                val first = context.load(20, PagingRequest.Refresh)
                assertEquals(
                    listOf(root.id),
                    first.data.map {
                        it
                            .asTimelinePostItem()
                            ?.post
                            ?.statusKey
                            ?.id
                    },
                )
                val thread = context.load(20, PagingRequest.Append(assertNotNull(first.nextKey)))
                assertEquals(
                    listOf(root.id, reply.id),
                    thread.data.map {
                        it
                            .asTimelinePostItem()
                            ?.post
                            ?.statusKey
                            ?.id
                    },
                )
                assertNull(thread.nextKey)
                assertTrue(context.load(20, PagingRequest.Prepend("before")).data.isEmpty())
                val loader = NostrLoader(account, manager)
                assertEquals(setOf(RelationActionType.Follow, RelationActionType.Block, RelationActionType.Mute), loader.supportedTypes)
                assertEquals(account, loader.userById(account.id).key)
                assertEquals(
                    root.id,
                    loader
                        .status(key)
                        .asTimelinePostItem()
                        ?.post
                        ?.statusKey
                        ?.id,
                )
                assertTrue(source.profileTabs(account).isNotEmpty())
            }
        }

    @Test
    fun postActionsPersistReturnedIdsForUndoAndPublishReports() =
        runTest {
            withDataSource { source, network, _ ->
                val target = nostrTestEvent(author = 1)
                network.storedEvents += target
                val key = MicroBlogKey(target.id, "nostr")
                val updates = mutableListOf<ActionMenu.Item>()
                val updater =
                    object : DatabaseUpdater {
                        override suspend fun updateCache(
                            postKey: MicroBlogKey,
                            update: suspend (UiTimelineV2) -> UiTimelineV2,
                        ) = error("Unexpected cache update")

                        override suspend fun deleteFromCache(postKey: MicroBlogKey) = error("Unexpected cache deletion")

                        override suspend fun updateActionMenu(
                            postKey: MicroBlogKey,
                            newActionMenu: ActionMenu.Item,
                        ) {
                            assertEquals(key, postKey)
                            updates += newActionMenu
                        }
                    }
                source.handle(PostEvent.Nostr.Like(key, null, 2, account), updater)
                source.handle(PostEvent.Nostr.Repost(key, null, 4, account), updater)
                val like = assertIs<PostEvent.Nostr.Like>(updates[0].clickEvent.postEventOrNull()?.postEvent)
                val repost = assertIs<PostEvent.Nostr.Repost>(updates[1].clickEvent.postEventOrNull()?.postEvent)
                assertEquals(3L, like.count)
                assertEquals(5L, repost.count)
                assertTrue(network.published.any { it.id == like.reactionEventId && it.kind == 7 })
                assertTrue(network.published.any { it.id == repost.repostEventId && it.kind == 6 })
                network.storedEvents += network.published.distinctBy { it.id }
                source.handle(like, updater)
                source.handle(repost, updater)
                assertEquals(
                    setOf(like.reactionEventId, repost.repostEventId),
                    network.published
                        .filter { it.kind == 5 }
                        .map { it.tags.single()[1] }
                        .toSet(),
                )
                source.handle(PostEvent.Nostr.Report(key, account), updater)
                assertTrue(
                    network.published.any {
                        it.kind == 1984 &&
                            it.tags.any { tag ->
                                tag.toList() == listOf("e", target.id, "spam")
                            }
                    },
                )
            }
        }

    @Test
    fun composeDispatchesNotesRepliesAndQuotes() =
        runTest {
            withDataSource { source, network, _ ->
                val target = nostrTestEvent(author = 1)
                network.storedEvents += target
                val key = MicroBlogKey(target.id, "nostr")
                val statuses = listOf(null, ComposeStatus.Reply(key), ComposeStatus.Quote(key))
                for ((index, status) in statuses.withIndex()) {
                    source.compose(ComposeData(content = "message $index", referenceStatus = status?.let(ComposeData::ReferenceStatus))) {
                        error("No media to upload")
                    }
                    val event = network.published.first { it.content == "message $index" }
                    assertEquals(account.id, event.pubKey)
                    assertEquals(1, event.kind)
                    if (index == 1) assertTrue(event.tags.any { it.toList() == listOf("e", target.id, "", "reply") })
                    if (index == 2) assertTrue(event.tags.any { it.first() == "q" && it[1] == target.id })
                }
            }
        }

    private suspend fun TestScope.withDataSource(block: suspend (NostrDataSource, QuartzTestRelays, NostrServiceManager) -> Unit) {
        stopKoin()
        startKoin { modules(module { single<CoroutineScope> { backgroundScope } }) }
        QuartzTestRelays(this).use { network ->
            val credentials = MutableStateFlow(credential.copy(relays = network.relays.map { it.url }))
            val manager = SwitchingServiceManager(credentials, backgroundScope) { network.service(it) }
            NostrDataSource(account, credentials, manager).use { block(it, network, manager) }
        }
    }
}
