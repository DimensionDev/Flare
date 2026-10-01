package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.data.platform.NostrCredential
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

class NostrFeatureTest {
    private val me = nostrTestSigner().pubKey
    private val other = nostrTestSigner(1).pubKey
    private val third = nostrTestSigner(2).pubKey

    @BeforeTest
    fun setUp() {
        stopKoin()
        startKoin()
    }

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun fetchesQuoteGraphAndRepostTargetsThatAreNotEmbedded() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val original = nostrTestEvent("original", author = 1)
                val quote = nostrTestEvent("quote", tags = listOf(listOf("q", original.id, "", other)))
                val repost = nostrTestEvent("", kind = 6, tags = listOf(listOf("e", original.id)))
                val genericRepost = nostrTestEvent("malformed embedded JSON", kind = 16, tags = listOf(listOf("e", original.id)))
                network.storedEvents += listOf(original, quote, repost, genericRepost)
                network.service().use { service ->
                    val quoted = assertNotNull(service.loadStatus(quote.key()).asTimelinePostItem())
                    assertEquals(
                        original.id,
                        quoted.presentation.quotes
                            .single()
                            .statusKey.id,
                    )
                    for (event in listOf(repost, genericRepost)) {
                        val result = assertNotNull(service.loadStatus(event.key()).asTimelinePostItem())
                        assertEquals(event.id, result.post.statusKey.id)
                        assertEquals(
                            original.id,
                            result.presentation.repost
                                ?.statusKey
                                ?.id,
                        )
                        assertEquals("original", result.displayPost.content.original.raw)
                    }
                }
            }
        }

    @Test
    fun cachedProfileAndPostAllowRenderingAndQuotingWhenRelayMetadataIsMissing() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val target = nostrTestEvent(author = 1)
                network.storedEvents += target
                val cached = network.service().use { assertNotNull(it.loadStatus(target.key()).asTimelinePostItem()).post }
                val profile = assertNotNull(cached.user)
                val cache =
                    object : NostrCache {
                        override suspend fun getProfiles(pubKeys: List<String>): Map<String, UiProfile> = mapOf(other to profile)

                        override suspend fun getPost(
                            accountKey: MicroBlogKey,
                            statusKey: MicroBlogKey,
                        ): UiTimelineV2.Post? = cached.takeIf { statusKey.id == target.id }
                    }
                network.service(cache = cache).use { service ->
                    assertEquals(profile.name.raw, service.loadProfile(other).name.raw)
                    network.queries.clear()
                    assertEquals(
                        cached.user,
                        service
                            .loadStatus(target.key())
                            .asTimelinePostItem()
                            ?.post
                            ?.user,
                    )
                    assertFalse(network.queries.flatMap { it.second }.any { it["kinds"].toString() == "[0]" })
                    network.storedEvents.clear()
                    val id = service.composeQuote(target.key(), "offline cached quote")
                    val published = network.published.first { it.id == id }
                    assertEquals(target.id, published.tags.single { it[0] == "q" }[1])
                    assertEquals(other, published.tags.single { it[0] == "p" }[1])
                    assertTrue(published.verify())
                }
            }
        }

    @Test
    fun homeUsesNewestContactsIncludesSelfAndPagesByTimestamp() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val own = nostrTestEvent(time = 100)
                val followed = nostrTestEvent(time = 99, author = 1)
                network.storedEvents +=
                    listOf(
                        nostrTestEvent(kind = 3, time = 1, tags = listOf(listOf("p", third))),
                        nostrTestEvent(kind = 3, time = 2, tags = listOf(listOf("p", other), listOf("p", other), listOf("p", "invalid"))),
                        own,
                        followed,
                        nostrTestEvent(time = 101, author = 2),
                        nostrTestEvent(kind = 30023, author = 1),
                    )
                network.service().use { service ->
                    assertEquals(listOf(own.id, followed.id), service.loadHomeTimeline(20, null).ids())
                    assertEquals(listOf(followed.id), service.loadHomeTimeline(20, 99).ids())
                    assertTrue(service.loadHomeTimeline(20, 98).isEmpty())
                }
                assertTrue(network.queries.flatMap { it.second }.any { it["limit"]?.jsonPrimitive?.content == "20" })
            }
        }

    @Test
    fun homeWithoutContactsAndUserTimelineExcludeOtherAuthors() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val own = nostrTestEvent(time = 100)
                val theirs = nostrTestEvent(time = 101, author = 1)
                val repost = nostrTestEvent(kind = 6, content = own.toJson(), time = 102, author = 1, tags = listOf(listOf("e", own.id)))
                network.storedEvents += listOf(own, theirs, repost)
                network.service().use { service ->
                    assertEquals(listOf(own.id), service.loadHomeTimeline(20, null).ids())
                    val timeline = service.loadUserTimeline(other, 20, null, false)
                    assertEquals(listOf(repost.id, theirs.id), timeline.ids())
                    assertEquals(
                        own.id,
                        timeline
                            .first()
                            .asTimelinePostItem()
                            ?.presentation
                            ?.repost
                            ?.statusKey
                            ?.id,
                    )
                    assertEquals(listOf(theirs.id), service.loadUserTimeline(other, 20, 101, false).ids())
                    assertTrue(service.loadUserTimeline(other, 20, null, true).isEmpty())
                }
            }
        }

    @Test
    fun textSearchFiltersIrrelevantEventsSortsAndLimitsResults() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val first = nostrTestEvent("Quartz migration", time = 10)
                val second = nostrTestEvent("QUARTZ works", time = 20, author = 1)
                network.storedEvents += listOf(first, second, nostrTestEvent("unrelated"), nostrTestEvent("Quartz", kind = 0))
                network.service().use { service ->
                    assertEquals(listOf(second.id), service.searchStatus("  quartz  ", 1, null).ids())
                    assertEquals(listOf(first.id), service.searchStatus("quartz", 10, 10).ids())
                    assertTrue(service.searchStatus("missing", 10, null).isEmpty())
                    val queryCount = network.queries.size
                    assertTrue(service.searchStatus("  ", 10, null).isEmpty())
                    assertTrue(service.searchUser("  ", 10).isEmpty())
                    assertEquals(queryCount, network.queries.size)
                }
            }
        }

    @Test
    fun searchesStatusAndProfileUsingEverySupportedNip19Identifier() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val note = nostrTestEvent()
                network.storedEvents += note
                network.service().use { service ->
                    val eventIds = listOf(note.id.uppercase(), NNote.create(note.id), NEvent.create(note.id, me, 1, relay = null))
                    for (id in eventIds.flatMap { listOf(it, "nostr:$it") }) {
                        assertEquals(listOf(note.id), service.searchStatus(id, 10, null).ids(), id)
                    }
                    val profileIds = listOf(me.uppercase(), nostrBech32PublicKey(me), NProfile.create(me, relay = null))
                    for (id in profileIds.flatMap { listOf(it, "nostr:$it") }) {
                        assertEquals(listOf(me), service.searchUser(id, 10).map { it.key.id }, id)
                    }
                    assertTrue(service.searchStatus("f".repeat(64), 10, null).isEmpty())
                }
            }
        }

    @Test
    fun profileSearchUsesLatestParsableMetadataAndAllSearchableFields() =
        runTest {
            QuartzTestRelays(this).use { network ->
                network.storedEvents +=
                    listOf(
                        nostrTestEvent("""{"name":"old-name"}""", kind = 0, time = 1),
                        nostrTestEvent(
                            """{"name":"alice","display_name":"Alice Example","nip05":"alice@example.com","about":"Kotlin developer"}""",
                            kind = 0,
                            time = 2,
                        ),
                        nostrTestEvent("broken JSON", kind = 0, time = 3),
                        nostrTestEvent("""{"name":"other"}""", kind = 0, author = 1),
                    )
                network.service().use { service ->
                    for (query in listOf("ALICE", "Example", "example.com", "developer")) {
                        assertEquals(listOf(me), service.searchUser(query, 1).map { it.key.id }, query)
                    }
                    assertTrue(service.searchUser("old-name", 10).isEmpty())
                    val profile = service.loadProfile(me)
                    assertEquals("Alice Example", profile.name.raw)
                    assertEquals("alice", profile.handle.raw)
                    assertEquals("Kotlin developer", profile.description?.raw)
                    assertFalse(UiProfile.Mark.Verified in profile.mark)
                    assertEquals(other, service.loadProfile(other).key.id)
                    assertEquals(third, service.loadProfile(third).key.id)
                }
            }
        }

    @Test
    fun notificationsCoverMentionsRepliesLikesAndBothRepostKinds() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val target = nostrTestEvent(time = 1)
                val p = listOf("p", me)
                val mention = nostrTestEvent("hello", time = 10, author = 1, tags = listOf(p))
                val reply = nostrTestEvent("reply", time = 11, author = 1, tags = listOf(p, listOf("e", target.id, "", "reply")))
                val like = nostrTestEvent("+", kind = 7, time = 12, author = 1, tags = listOf(p, listOf("e", target.id)))
                val reposts =
                    listOf(6, 16).map { kind ->
                        nostrTestEvent(target.toJson(), kind, time = kind + 10L, author = 1, tags = listOf(p, listOf("e", target.id)))
                    }
                network.storedEvents += listOf(target, mention, reply, like) + reposts +
                    listOf(
                        nostrTestEvent("self", time = 30, tags = listOf(p)),
                        nostrTestEvent("-", kind = 7, time = 31, author = 1, tags = listOf(p, listOf("e", target.id))),
                        nostrTestEvent("other recipient", time = 32, author = 1, tags = listOf(listOf("p", third))),
                    )
                network.service().use { service ->
                    val all = service.loadNotifications(20, null, NotificationFilter.All).map { assertNotNull(it.asTimelinePostItem()) }
                    assertEquals(
                        listOf(UiIcon.Retweet, UiIcon.Retweet, UiIcon.Like, UiIcon.Reply, UiIcon.Mention),
                        all.map { it.presentation.message?.icon },
                    )
                    assertTrue(
                        all.all {
                            it.presentation.message
                                ?.user
                                ?.key
                                ?.id == other
                        },
                    )
                    assertEquals(target.id, all[2].displayPost.statusKey.id)
                    assertEquals(listOf(reply.id, mention.id), service.loadNotifications(20, null, NotificationFilter.Mention).ids())
                    assertEquals(listOf(mention.id), service.loadNotifications(20, 10, NotificationFilter.Mention).ids())
                    assertEquals(1, service.loadNotifications(1, 10, NotificationFilter.All).size)
                }
            }
        }

    @Test
    fun contextLoadsAncestorChainAndOnlyDirectRepliesInOrder() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val root = nostrTestEvent(time = 1)
                val parent = nostrTestEvent(time = 2, tags = listOf(listOf("e", root.id)))
                val focal = nostrTestEvent(time = 3, tags = listOf(listOf("e", root.id, "", "root"), listOf("e", parent.id, "", "reply")))
                val reply = nostrTestEvent(time = 4, tags = listOf(listOf("e", root.id, "", "root"), listOf("e", focal.id, "", "reply")))
                val later = nostrTestEvent(time = 5, tags = listOf(listOf("e", focal.id)))
                val grandchild =
                    nostrTestEvent(time = 6, tags = listOf(listOf("e", focal.id, "", "root"), listOf("e", reply.id, "", "reply")))
                val quote = nostrTestEvent(time = 7, tags = listOf(listOf("e", focal.id, "", "mention")))
                network.storedEvents += listOf(quote, later, grandchild, reply, focal, parent, root)
                network.service().use { service ->
                    assertEquals(listOf(root.id, parent.id, focal.id, reply.id, later.id), service.loadStatusContext(focal.key(), 20).ids())
                    assertEquals(listOf(root.id, parent.id, focal.id, reply.id), service.loadStatusContext(focal.key(), 1).ids())
                    assertEquals(
                        listOf(root.id, parent.id),
                        service
                            .loadStatus(focal.key())
                            .asTimelinePostItem()
                            ?.presentation
                            ?.inlineParents
                            ?.ids(),
                    )
                }
            }
        }

    @Test
    fun missingTargetsFailWritesAndUnresolvedReferencesDoNotHideReadablePosts() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val missing = MicroBlogKey("f".repeat(64), "nostr")
                val note = nostrTestEvent(tags = listOf(listOf("e", missing.id, "", "reply"), listOf("q", missing.id)))
                network.storedEvents += note
                network.service().use { service ->
                    assertEquals(
                        note.id,
                        service
                            .loadStatus(note.key())
                            .asTimelinePostItem()
                            ?.post
                            ?.statusKey
                            ?.id,
                    )
                    assertEquals(listOf(note.id), service.loadStatusContext(note.key(), 20).ids())
                    val actions: List<suspend () -> Unit> =
                        listOf(
                            { service.loadStatus(missing) },
                            { service.loadStatusContext(missing, 20) },
                            { service.composeReply(missing, "reply") },
                            { service.composeQuote(missing, "quote") },
                            { service.react(missing) },
                            { service.repost(missing) },
                            { service.report(missing) },
                            { service.deleteStatus(missing) },
                        )
                    for (action in actions) assertFailsWith<IllegalStateException> { action() }
                    assertTrue(network.published.isEmpty())
                }
            }
        }

    @Test
    fun interactionsDeduplicateRelaysIgnoreNegativeReactionsAndRetainUndoIds() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val target = nostrTestEvent(author = 1)
                val tags = listOf(listOf("e", target.id), listOf("p", other))
                val like = nostrTestEvent("+", kind = 7, tags = tags)
                val repost = nostrTestEvent(target.toJson(), kind = 6, tags = tags)
                network.storedEvents +=
                    listOf(
                        target,
                        like,
                        like,
                        repost,
                        nostrTestEvent("", kind = 7, tags = tags, author = 2),
                        nostrTestEvent("-", kind = 7, tags = tags, author = 1),
                        nostrTestEvent(target.toJson(), kind = 16, tags = tags, author = 2),
                    )
                network.service().use { service ->
                    val post = assertNotNull(service.loadStatus(target.key()).asTimelinePostItem()).post
                    val likeAction = post.actions.filterIsInstance<ActionMenu.Item>().single { it.actionFamily == PostActionFamily.Like }
                    val repostAction =
                        post.actions
                            .filterIsInstance<ActionMenu.Group>()
                            .single {
                                it.displayItem.actionFamily ==
                                    PostActionFamily.Repost
                            }.displayItem
                    assertEquals(2L, likeAction.count?.value)
                    assertEquals(2L, repostAction.count?.value)
                    assertEquals(ActionMenu.Item.Text.Localized.Type.Unlike, assertIs<ActionMenu.Item.Text.Localized>(likeAction.text).type)
                    assertEquals(
                        ActionMenu.Item.Text.Localized.Type.Unretweet,
                        assertIs<ActionMenu.Item.Text.Localized>(repostAction.text).type,
                    )
                    service.deleteStatus(like.key())
                    service.deleteStatus(repost.key())
                    assertEquals(setOf(like.id, repost.id), network.published.map { it.tags.single()[1] }.toSet())
                    assertTrue(network.published.all { it.kind == 5 && it.verify() })
                }
            }
        }

    @Test
    fun mediaRenderingPreservesTypesAltDimensionsWarningsAndDeduplicatesUrls() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val base = "https://media.example/"
                val note =
                    nostrTestEvent(
                        "hello\n${base}image.JPG?x=1\n${base}clip.mp4\n${base}anim.gif\n${base}audio.ogg",
                        tags =
                            listOf(
                                listOf("imeta", "url ${base}image.JPG?x=1", "dim 640x480", "alt description"),
                                listOf("r", "${base}image.JPG?x=1"),
                                listOf("r", "${base}clip.mp4"),
                                listOf("content-warning", "  spoiler  "),
                            ),
                    )
                network.storedEvents += note
                network.service().use { service ->
                    val post = assertNotNull(service.loadStatus(note.key()).asTimelinePostItem()).post
                    assertEquals(4, post.images.size)
                    val image = assertIs<UiMedia.Image>(post.images[0])
                    assertEquals(640f, image.width)
                    assertEquals(480f, image.height)
                    assertEquals("description", image.description)
                    assertIs<UiMedia.Video>(post.images[1])
                    assertIs<UiMedia.Gif>(post.images[2])
                    assertIs<UiMedia.Audio>(post.images[3])
                    assertEquals("spoiler", post.contentWarning?.original?.raw)
                    assertEquals(
                        "hello",
                        post.content.original.raw
                            .trim(),
                    )
                }
            }
        }

    @Test
    fun relationUsesNewestListsAndKeepsOtherUsersDuringEveryUpdate() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val types = listOf(3, 10000, 30000)
                for (kind in types) {
                    val d = if (kind == 30000) listOf(listOf("d", "mute")) else emptyList()
                    network.storedEvents += nostrTestEvent(kind = kind, time = 1, tags = d + listOf(listOf("p", other)))
                    network.storedEvents += nostrTestEvent(kind = kind, time = 2, tags = d + listOf(listOf("p", third)))
                }
                network.storedEvents += nostrTestEvent(kind = 30000, tags = listOf(listOf("d", "other-list"), listOf("p", other)))
                network.service().use { service ->
                    val old = service.relation(other)
                    assertFalse(old.following || old.blocking || old.muted)
                    val current = service.relation(third)
                    assertTrue(current.following && current.blocking && current.muted)
                    val actions =
                        listOf<Pair<suspend () -> Unit, Int>>(
                            suspend { service.follow(other) } to 3,
                            suspend { service.mute(other) } to 10000,
                            suspend { service.block(other) } to 30000,
                        )
                    for ((action, kind) in actions) {
                        network.published.clear()
                        action()
                        val event = network.published.distinctBy { it.id }.single()
                        assertEquals(kind, event.kind)
                        assertEquals(setOf(other, third), event.tags.userIdSet())
                        network.storedEvents += event
                    }
                    assertTrue(service.relation(other).let { it.following && it.blocking && it.muted })
                    for ((action, kind) in listOf<Pair<suspend () -> Unit, Int>>(
                        suspend { service.unfollow(other) } to 3,
                        suspend { service.unmute(other) } to 10000,
                        suspend { service.unblock(other) } to 30000,
                    )) {
                        network.published.clear()
                        action()
                        val event = network.published.distinctBy { it.id }.single()
                        assertEquals(kind, event.kind)
                        assertEquals(setOf(third), event.tags.userIdSet())
                    }
                }
            }
        }

    @Test
    fun composedMediaNotesRepliesAndQuotesPreserveMetadataAndThreadRoot() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val root = nostrTestEvent(author = 1)
                val target = nostrTestEvent("reply", author = 1, tags = listOf(listOf("e", root.id, "", "root")))
                network.storedEvents += listOf(root, target)
                val media = UploadedMedia("https://example.com/a.png", "image/png", "abc", 123, "alt text")
                network.service().use { service ->
                    val actions: List<suspend () -> String> =
                        listOf(
                            { service.composeNote("content  ", listOf(media), "  warning  ") },
                            { service.composeReply(target.key(), "content  ", listOf(media), "  warning  ") },
                            { service.composeQuote(target.key(), "content  ", listOf(media), "  warning  ") },
                        )
                    for ((index, action) in actions.withIndex()) {
                        val id = action()
                        val event = network.published.first { it.id == id }
                        assertTrue(event.verify())
                        assertEquals(me, event.pubKey)
                        assertEquals("content\n${media.url}", event.content)
                        val tags = event.tags.map { it.toList() }
                        assertTrue(listOf("content-warning", "warning") in tags)
                        assertTrue(listOf("imeta", "url ${media.url}", "m image/png", "x abc", "size 123", "alt alt text") in tags)
                        assertTrue(listOf("r", media.url) in tags)
                        if (index == 1) {
                            assertTrue(listOf("e", root.id, "", "root") in tags)
                            assertTrue(listOf("e", target.id, "", "reply") in tags)
                        }
                        if (index == 2) assertTrue(listOf("q", target.id, "", other) in tags)
                    }
                    val mediaOnlyId = service.composeNote("", listOf(media), " ")
                    val mediaOnly = network.published.first { it.id == mediaOnlyId }
                    assertEquals(media.url, mediaOnly.content)
                    assertFalse(mediaOnly.tags.any { it[0] == "content-warning" })
                }
            }
        }

    @Test
    fun readOnlySupportsReadsButRejectsEveryPublishingOperation() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val target = nostrTestEvent()
                network.storedEvents += listOf(target) +
                    listOf(3, 10000, 30000).map {
                        nostrTestEvent(kind = it, tags = listOf(listOf("d", "mute"), listOf("p", other)))
                    }
                network.service(NostrCredential(pubkeyHex = me)).use { service ->
                    assertFalse(service.canSign)
                    assertEquals(listOf(target.id), service.loadHomeTimeline(20, null).ids())
                    assertEquals(me, service.loadProfile(me).key.id)
                    assertTrue(service.relation(other).following)
                    val actions: List<suspend () -> Unit> =
                        listOf(
                            { service.composeNote("no") },
                            { service.composeReply(target.key(), "no") },
                            { service.composeQuote(target.key(), "no") },
                            { service.react(target.key()) },
                            { service.repost(target.key()) },
                            { service.report(target.key()) },
                            { service.deleteStatus(target.key()) },
                            { service.follow(other) },
                            { service.unfollow(other) },
                            { service.block(other) },
                            { service.unblock(other) },
                            { service.mute(other) },
                            { service.unmute(other) },
                            { service.buildBlossomUploadAuthEvent("hash") },
                        )
                    for (action in actions) {
                        assertTrue(assertFailsWith<IllegalStateException> { action() }.message.orEmpty().contains("read-only"))
                    }
                    assertTrue(network.published.isEmpty())
                }
            }
        }

    @Test
    fun relayChangesAffectSubsequentQueriesAndPublishingWithoutChangingIdentity() =
        runTest {
            QuartzTestRelays(this).use { network ->
                network.service().use { service ->
                    val relay = network.relays.last()
                    service.updateRelays(listOf("  ${relay.url}  ", relay.url))
                    val id = service.composeNote("updated relay")
                    assertEquals(me, network.published.first { it.id == id }.pubKey)
                    service.loadProfile(other)
                    assertEquals(setOf(relay), network.queries.map { it.first }.toSet())
                    assertFailsWith<IllegalArgumentException> { service.updateRelays(listOf("not a relay")) }
                    service.updateRelays(emptyList())
                    network.queries.clear()
                    service.loadProfile(other)
                    assertEquals(normalizedNostrRelays(defaultNostrRelays), network.queries.map { it.first }.toSet())
                }
            }
        }

    @Test
    fun relayDiscoveryPrefersNewestNip65ThenContactTagsThenLegacyContent() =
        runTest {
            val old = nostrTestEvent(kind = 10002, time = 1, tags = listOf(listOf("r", "wss://old.example")))
            val latest = nostrTestEvent(kind = 10002, time = 2, tags = listOf(listOf("r", "wss://new.example", "read")))
            val contacts = nostrTestEvent(kind = 3, tags = listOf(listOf("r", "wss://contacts.example")))
            val legacy = nostrTestEvent("""{"wss://legacy.example":{"read":true,"write":true}}""", kind = 3)

            fun extract(vararg events: QuartzEvent) = NostrService.extractRelayUrls(events.map { it.toCompatEvent() })
            assertEquals(listOf("wss://new.example"), extract(old, latest, contacts))
            assertEquals(listOf("wss://contacts.example"), extract(contacts))
            assertEquals(listOf("wss://legacy.example"), extract(legacy))
            assertEquals(listOf("wss://contacts.example"), extract(nostrTestEvent(kind = 10002), contacts))
            assertTrue(extract(nostrTestEvent("invalid JSON", kind = 3)).isEmpty())
            assertTrue(extract().isEmpty())
        }

    private fun QuartzEvent.key() = MicroBlogKey(id, "nostr")

    private fun List<UiTimelineV2>.ids() = map { assertNotNull(it.asTimelinePostItem()).post.statusKey.id }
}
