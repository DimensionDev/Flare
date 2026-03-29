package dev.dimension.flare.data.database.cache.mapper

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.database.cache.model.translationEntityKey
import dev.dimension.flare.data.database.cache.model.translationPayload
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class MicroblogTest : RobolectricTest() {
    private lateinit var db: CacheDatabase

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        startKoin {
            modules(
                module {
                    single { db }
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun saveToDatabasePersistsUserAndStatus() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val user = createUser(MicroBlogKey(id = "user-1", host = "test.com"), "User One")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = user,
                    statusKey = MicroBlogKey(id = "status-1", host = "test.com"),
                    text = "status text",
                )

            val timelineItem = TimelinePagingMapper.toDb(post, pagingKey = "home")
            saveToDatabase(db, listOf(timelineItem))

            val savedUser = db.userDao().findByKey(user.key).first()
            assertNotNull(savedUser)
            assertEquals(user.key, savedUser.userKey)
            assertEquals("User One", savedUser.content.name.raw)

            val savedStatus = db.statusDao().get(post.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedStatus)
            assertEquals(post.statusKey, savedStatus.content.statusKey)
            requireNotNull(savedStatus.text)
            kotlin.test.assertTrue(savedStatus.text.contains("status text"))
        }

    @Test
    fun saveToDatabaseUpdatesUserWhenUserChanged() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val statusKey = MicroBlogKey(id = "status-user-update", host = "test.com")
            val userKey = MicroBlogKey(id = "user-update", host = "test.com")
            val initialUser = createUser(userKey, "Old Name")
            val updatedUser = createUser(userKey, "New Name")

            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(
                        createPost(
                            accountKey = accountKey,
                            user = initialUser,
                            statusKey = statusKey,
                            text = "status text",
                        ),
                        pagingKey = "home",
                    ),
                ),
            )

            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(
                        createPost(
                            accountKey = accountKey,
                            user = updatedUser,
                            statusKey = statusKey,
                            text = "status text",
                        ),
                        pagingKey = "home",
                    ),
                ),
            )

            val savedUser = db.userDao().findByKey(userKey).first()
            assertNotNull(savedUser)
            assertEquals("New Name", savedUser.content.name.raw)
        }

    @Test
    fun saveToDatabaseUpdatesStatusWhenStatusChanged() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val statusKey = MicroBlogKey(id = "status-update", host = "test.com")
            val user = createUser(MicroBlogKey(id = "status-update-user", host = "test.com"), "Status User")

            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(
                        createPost(
                            accountKey = accountKey,
                            user = user,
                            statusKey = statusKey,
                            text = "old status text",
                        ),
                        pagingKey = "home",
                    ),
                ),
            )

            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(
                        createPost(
                            accountKey = accountKey,
                            user = user,
                            statusKey = statusKey,
                            text = "new status text",
                        ),
                        pagingKey = "home",
                    ),
                ),
            )

            val savedStatus = db.statusDao().get(statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedStatus)
            requireNotNull(savedStatus.text)
            assertTrue(savedStatus.text.contains("new status text"))
        }

    @Test
    fun saveToDatabasePersistsReferences() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")

            val refUser = createUser(MicroBlogKey(id = "ref-user", host = "test.com"), "Ref User")
            val refPost =
                createPost(
                    accountKey = accountKey,
                    user = refUser,
                    statusKey = MicroBlogKey(id = "ref-status", host = "test.com"),
                    text = "ref status",
                )

            val mainUser = createUser(MicroBlogKey(id = "main-user", host = "test.com"), "Main User")
            val mainPost =
                createPost(
                    accountKey = accountKey,
                    user = mainUser,
                    statusKey = MicroBlogKey(id = "main-status", host = "test.com"),
                    text = "main status",
                    parents = persistentListOf(refPost),
                )

            val timelineItem = TimelinePagingMapper.toDb(mainPost, pagingKey = "home")
            saveToDatabase(db, listOf(timelineItem))

            val savedMainStatus = db.statusDao().get(mainPost.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedMainStatus)
            val savedMainPost = assertIs<UiTimelineV2.Post>(savedMainStatus.content)
            assertTrue(savedMainPost.parents.isEmpty())
            assertEquals(1, savedMainPost.references.size)
            assertEquals(ReferenceType.Reply, savedMainPost.references.first().type)
            assertEquals(refPost.statusKey, savedMainPost.references.first().statusKey)
            val savedRefStatus = db.statusDao().get(refPost.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedRefStatus)

            val savedReferences = db.statusReferenceDao().getByStatusKey(mainPost.statusKey)
            assertEquals(1, savedReferences.size)
            assertEquals(refPost.statusKey, savedReferences.first().referenceStatusKey)
        }

    @Test
    fun saveToDatabaseStillPersistsUsersForPostUserAndUserListTimeline() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val firstUser = createUser(MicroBlogKey(id = "user-1", host = "test.com"), "First User")
            val secondUser = createUser(MicroBlogKey(id = "user-2", host = "test.com"), "Second User")
            val postUser = createUser(MicroBlogKey(id = "user-3", host = "test.com"), "Post User")

            val userTimeline =
                UiTimelineV2.User(
                    message = null,
                    value = firstUser,
                    createdAt = Clock.System.now().toUi(),
                    statusKey = MicroBlogKey(id = "timeline-user", host = "test.com"),
                    accountType = AccountType.Specific(accountKey),
                )
            val userListTimeline =
                UiTimelineV2.UserList(
                    message = null,
                    users = persistentListOf(firstUser, secondUser),
                    createdAt = Clock.System.now().toUi(),
                    statusKey = MicroBlogKey(id = "timeline-user-list", host = "test.com"),
                    post = null,
                    accountType = AccountType.Specific(accountKey),
                )
            val postTimeline =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey = MicroBlogKey(id = "timeline-post", host = "test.com"),
                    text = "post timeline",
                )

            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(postTimeline, pagingKey = "home"),
                    TimelinePagingMapper.toDb(userTimeline, pagingKey = "home"),
                    TimelinePagingMapper.toDb(userListTimeline, pagingKey = "home"),
                ),
            )

            val savedFirst = db.userDao().findByKey(firstUser.key).first()
            val savedSecond = db.userDao().findByKey(secondUser.key).first()
            val savedPostUser = db.userDao().findByKey(postUser.key).first()
            assertNotNull(savedFirst)
            assertNotNull(savedSecond)
            assertNotNull(savedPostUser)
            assertEquals("First User", savedFirst.content.name.raw)
            assertEquals("Second User", savedSecond.content.name.raw)
            assertEquals("Post User", savedPostUser.content.name.raw)
        }

    @Test
    fun replyReferencesRemainWhenSubsequentInsertHasNoReplyReferences() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")

            val refUser = createUser(MicroBlogKey(id = "ref-user", host = "test.com"), "Ref User")
            val refPost =
                createPost(
                    accountKey = accountKey,
                    user = refUser,
                    statusKey = MicroBlogKey(id = "ref-status", host = "test.com"),
                    text = "ref status",
                )

            val mainUser = createUser(MicroBlogKey(id = "main-user", host = "test.com"), "Main User")
            val withRef =
                createPost(
                    accountKey = accountKey,
                    user = mainUser,
                    statusKey = MicroBlogKey(id = "main-status", host = "test.com"),
                    text = "main status",
                    parents = persistentListOf(refPost),
                )

            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withRef, pagingKey = "home")))
            assertEquals(1, db.statusReferenceDao().getByStatusKey(withRef.statusKey).size)

            val withoutRef = withRef.copy(parents = persistentListOf())
            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withoutRef, pagingKey = "home")))

            val refsAfter = db.statusReferenceDao().getByStatusKey(withRef.statusKey)
            assertEquals(1, refsAfter.size)

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(refreshResult)
        }

    @Test
    fun staleQuoteReferencesAreRemovedWhenSubsequentInsertHasNoQuoteReferences() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")

            val quoteUser = createUser(MicroBlogKey(id = "quote-user", host = "test.com"), "Quote User")
            val quotePost =
                createPost(
                    accountKey = accountKey,
                    user = quoteUser,
                    statusKey = MicroBlogKey(id = "quote-status", host = "test.com"),
                    text = "quote status",
                )

            val mainUser = createUser(MicroBlogKey(id = "main-user-quote", host = "test.com"), "Main User")
            val withQuote =
                createPost(
                    accountKey = accountKey,
                    user = mainUser,
                    statusKey = MicroBlogKey(id = "main-status-quote", host = "test.com"),
                    text = "main status",
                    quote = persistentListOf(quotePost),
                )

            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withQuote, pagingKey = "home")))
            assertEquals(1, db.statusReferenceDao().getByStatusKey(withQuote.statusKey).size)

            val withoutQuote = withQuote.copy(quote = persistentListOf())
            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withoutQuote, pagingKey = "home")))

            val refsAfter = db.statusReferenceDao().getByStatusKey(withQuote.statusKey)
            assertEquals(0, refsAfter.size)
        }

    @Test
    fun postContentReplyReferencesRemainWhenSubsequentInsertHasNoParents() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")

            val refUser = createUser(MicroBlogKey(id = "ref-user", host = "test.com"), "Ref User")
            val refPost =
                createPost(
                    accountKey = accountKey,
                    user = refUser,
                    statusKey = MicroBlogKey(id = "ref-status-2", host = "test.com"),
                    text = "ref status",
                )

            val mainUser = createUser(MicroBlogKey(id = "main-user", host = "test.com"), "Main User")
            val withParents =
                createPost(
                    accountKey = accountKey,
                    user = mainUser,
                    statusKey = MicroBlogKey(id = "main-status-2", host = "test.com"),
                    text = "main status",
                    parents = persistentListOf(refPost),
                )

            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withParents, pagingKey = "home")))
            val withoutParents = withParents.copy(parents = persistentListOf())
            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withoutParents, pagingKey = "post_only_${withParents.statusKey}")))

            val saved = db.statusDao().get(withParents.statusKey, AccountType.Specific(accountKey)).first()
            val savedPost = assertIs<UiTimelineV2.Post>(assertNotNull(saved).content)
            assertTrue(savedPost.parents.isEmpty())
            assertEquals(1, savedPost.references.size)
            assertEquals(refPost.statusKey, savedPost.references.first().statusKey)
        }

    @Test
    fun toDbMapsReplyReference() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val rootUser = createUser(MicroBlogKey(id = "root-user", host = "test.com"), "Root User")
            val parentUser = createUser(MicroBlogKey(id = "parent-user", host = "test.com"), "Parent User")
            val parentPost =
                createPost(
                    accountKey = accountKey,
                    user = parentUser,
                    statusKey = MicroBlogKey(id = "parent-status", host = "test.com"),
                    text = "parent",
                )
            val rootPost =
                createPost(
                    accountKey = accountKey,
                    user = rootUser,
                    statusKey = MicroBlogKey(id = "root-status", host = "test.com"),
                    text = "root",
                    parents = listOf(parentPost),
                )

            val mapped = TimelinePagingMapper.toDb(rootPost, pagingKey = "home")
            assertEquals(rootPost.statusKey, mapped.timeline.statusKey)
            assertEquals(1, mapped.status.references.size)
            val reference =
                mapped.status.references
                    .first()
                    .reference
            assertEquals(ReferenceType.Reply, reference.referenceType)
            assertEquals(rootPost.statusKey, reference.statusKey)
            assertEquals(parentPost.statusKey, reference.referenceStatusKey)
        }

    @Test
    fun toDbMapsRetweetReferenceFromInternalRepost() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val wrapperUser = createUser(MicroBlogKey(id = "wrapper-user", host = "test.com"), "Wrapper User")
            val repostUser = createUser(MicroBlogKey(id = "repost-user", host = "test.com"), "Repost User")
            val repostPost =
                createPost(
                    accountKey = accountKey,
                    user = repostUser,
                    statusKey = MicroBlogKey(id = "repost-status", host = "test.com"),
                    text = "repost",
                )
            val wrapperPost =
                createPost(
                    accountKey = accountKey,
                    user = wrapperUser,
                    statusKey = MicroBlogKey(id = "wrapper-status", host = "test.com"),
                    text = "wrapper",
                ).copy(
                    internalRepost = repostPost,
                )

            val mapped = TimelinePagingMapper.toDb(wrapperPost, pagingKey = "home")
            val retweetReference =
                mapped.status.references.find { it.reference.referenceType == ReferenceType.Retweet }
            assertNotNull(retweetReference)
            assertEquals(wrapperPost.statusKey, retweetReference.reference.statusKey)
            assertEquals(repostPost.statusKey, retweetReference.reference.referenceStatusKey)
        }

    @Test
    fun toUiSetsExtraKeyForRootAndReferences() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val rootUser = createUser(MicroBlogKey(id = "root-user", host = "test.com"), "Root User")
            val parentUser = createUser(MicroBlogKey(id = "parent-user", host = "test.com"), "Parent User")
            val parentPost =
                createPost(
                    accountKey = accountKey,
                    user = parentUser,
                    statusKey = MicroBlogKey(id = "parent-status", host = "test.com"),
                    text = "parent",
                )
            val rootPost =
                createPost(
                    accountKey = accountKey,
                    user = rootUser,
                    statusKey = MicroBlogKey(id = "root-status", host = "test.com"),
                    text = "root",
                    parents = listOf(parentPost),
                )

            val mapped = TimelinePagingMapper.toDb(rootPost, pagingKey = "home")
            val ui = TimelinePagingMapper.toUi(mapped, pagingKey = "home", useDbKeyInItemKey = true)
            val post = assertIs<UiTimelineV2.Post>(ui)
            assertEquals("home", post.extraKey)
            assertEquals(1, post.parents.size)
            assertEquals("home", post.parents.first().extraKey)
            assertEquals(parentPost.statusKey, post.parents.first().statusKey)
        }

    @Test
    fun toUiUsesCompletedTranslationForRootAndReplyReference() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val rootUser = createUser(MicroBlogKey(id = "root-user-translated", host = "test.com"), "Root User")
            val parentUser = createUser(MicroBlogKey(id = "parent-user-translated", host = "test.com"), "Parent User")
            val parentPost =
                createPost(
                    accountKey = accountKey,
                    user = parentUser,
                    statusKey = MicroBlogKey(id = "parent-status-translated", host = "test.com"),
                    text = "parent original",
                )
            val rootPost =
                createPost(
                    accountKey = accountKey,
                    user = rootUser,
                    statusKey = MicroBlogKey(id = "root-status-translated", host = "test.com"),
                    text = "root original",
                    parents = listOf(parentPost),
                )

            val mapped = TimelinePagingMapper.toDb(rootPost, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))
            val savedParentStatus = assertNotNull(db.statusDao().get(parentPost.statusKey, AccountType.Specific(accountKey)).first())
            db.translationDao().insertAll(
                listOf(
                    DbTranslation(
                        entityType = TranslationEntityType.Status,
                        entityKey =
                            mapped.status.status.data
                                .translationEntityKey(),
                        targetLanguage = Locale.language,
                        sourceHash = rootPost.translationPayload()!!.sourceHash(),
                        status = TranslationStatus.Completed,
                        payload = TranslationPayload(content = "根帖子".toUiPlainText()),
                        updatedAt = 1L,
                    ),
                    DbTranslation(
                        entityType = TranslationEntityType.Status,
                        entityKey = savedParentStatus.translationEntityKey(),
                        targetLanguage = Locale.language,
                        sourceHash = parentPost.translationPayload()!!.sourceHash(),
                        status = TranslationStatus.Completed,
                        payload = TranslationPayload(content = "父帖子".toUiPlainText()),
                        updatedAt = 1L,
                    ),
                ),
            )

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page = assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(refreshResult)
            val dbItem = assertNotNull(page.data.firstOrNull())

            val ui =
                TimelinePagingMapper.toUi(
                    item = dbItem,
                    pagingKey = "home",
                    useDbKeyInItemKey = false,
                    translationDisplayOptions =
                        TranslationDisplayOptions(
                            translationEnabled = true,
                            autoDisplayEnabled = true,
                        ),
                )
            val post = assertIs<UiTimelineV2.Post>(ui)

            assertEquals("根帖子", post.content.raw)
            assertEquals(1, post.parents.size)
            assertEquals(
                "父帖子",
                post.parents
                    .first()
                    .content.raw,
            )
        }

    @Test
    fun timelinePagingMapperKeepsPostMessageAfterRoundTrip() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val postUser = createUser(MicroBlogKey(id = "post-user", host = "test.com"), "Post User")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey = MicroBlogKey(id = "post-status", host = "test.com"),
                    text = "post content",
                ).copy(
                    message =
                        UiTimelineV2.Message(
                            user = null,
                            statusKey = MicroBlogKey(id = "message-status", host = "test.com"),
                            icon = dev.dimension.flare.ui.model.UiIcon.Retweet,
                            type =
                                UiTimelineV2.Message.Type.Localized(
                                    UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                                ),
                            createdAt = Clock.System.now().toUi(),
                            clickEvent = ClickEvent.Noop,
                            accountType = AccountType.Specific(accountKey),
                        ),
                )

            val mapped = TimelinePagingMapper.toDb(post, pagingKey = "home")
            val roundTrip = TimelinePagingMapper.toUi(mapped, pagingKey = "home", useDbKeyInItemKey = false)
            val rendered = assertIs<UiTimelineV2.Post>(roundTrip)
            val message = assertNotNull(rendered.message)
            val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)

            assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        }

    @Test
    fun toUiUsesEmbeddedUserDataWithoutReadingUserJoin() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val user = createUser(MicroBlogKey(id = "user-join", host = "test.com"), "Embedded User")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = user,
                    statusKey = MicroBlogKey(id = "status-join", host = "test.com"),
                    text = "post content",
                )

            val mapped = TimelinePagingMapper.toDb(post, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))

            val overwrittenUser =
                user.copy(
                    nameInternal = "Joined User".toUiPlainText(),
                )
            db.userDao().insert(overwrittenUser.toDbUser())

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page = assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(refreshResult)
            val dbItem =
                assertNotNull(
                    page.data.firstOrNull {
                        it.status.status.data.statusKey == post.statusKey
                    },
                )

            val rendered =
                assertIs<UiTimelineV2.Post>(
                    TimelinePagingMapper.toUi(
                        dbItem,
                        pagingKey = "home",
                        useDbKeyInItemKey = false,
                    ),
                )
            assertEquals("Embedded User", rendered.user?.name?.raw)
        }

    @Test
    fun toUiFlattensInternalRepostButKeepsReferencePayload() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val wrapperUser = createUser(MicroBlogKey(id = "wrapper-user", host = "test.com"), "Wrapper User")
            val repostUser = createUser(MicroBlogKey(id = "repost-user", host = "test.com"), "Repost User")
            val repostPost =
                createPost(
                    accountKey = accountKey,
                    user = repostUser,
                    statusKey = MicroBlogKey(id = "repost-status", host = "test.com"),
                    text = "repost content",
                )
            val repostMessage =
                UiTimelineV2.Message(
                    user = wrapperUser,
                    statusKey = MicroBlogKey(id = "wrapper-status", host = "test.com"),
                    icon = dev.dimension.flare.ui.model.UiIcon.Retweet,
                    type =
                        UiTimelineV2.Message.Type.Localized(
                            UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                        ),
                    createdAt = Clock.System.now().toUi(),
                    clickEvent = ClickEvent.Noop,
                    accountType = AccountType.Specific(accountKey),
                )
            val wrapperPost =
                createPost(
                    accountKey = accountKey,
                    user = wrapperUser,
                    statusKey = MicroBlogKey(id = "wrapper-status", host = "test.com"),
                    text = "wrapper content",
                ).copy(
                    message = repostMessage,
                    internalRepost = repostPost,
                )

            val mapped = TimelinePagingMapper.toDb(wrapperPost, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))

            val savedWrapper = db.statusDao().get(wrapperPost.statusKey, AccountType.Specific(accountKey)).first()
            val savedRepost = db.statusDao().get(repostPost.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedWrapper)
            assertNotNull(savedRepost)
            val savedWrapperPost = assertIs<UiTimelineV2.Post>(savedWrapper.content)
            assertTrue(savedWrapperPost.quote.isEmpty())
            assertTrue(savedWrapperPost.parents.isEmpty())
            kotlin.test.assertNull(savedWrapperPost.internalRepost)
            assertEquals(1, savedWrapperPost.references.size)
            assertEquals(ReferenceType.Retweet, savedWrapperPost.references.first().type)

            val roundTrip = TimelinePagingMapper.toUi(mapped, pagingKey = "home", useDbKeyInItemKey = false)
            val rendered = assertIs<UiTimelineV2.Post>(roundTrip)
            val internalRepost = assertNotNull(rendered.internalRepost)

            assertEquals(wrapperPost.statusKey, rendered.statusKey)
            assertEquals(repostPost.statusKey, internalRepost.statusKey)
            assertEquals(repostPost.content.raw, rendered.content.raw)
            assertEquals(repostPost.user?.key, rendered.user?.key)
            assertEquals(repostPost.content.raw, internalRepost.content.raw)

            val message = assertNotNull(rendered.message)
            val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)
            assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        }

    @Test
    fun repostWithQuoteChainIsStoredSeparatedAndFlattenedOnToUi() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val userA = createUser(MicroBlogKey(id = "user-a", host = "test.com"), "User A")
            val userB = createUser(MicroBlogKey(id = "user-b", host = "test.com"), "User B")
            val userC = createUser(MicroBlogKey(id = "user-c", host = "test.com"), "User C")

            val postC =
                createPost(
                    accountKey = accountKey,
                    user = userC,
                    statusKey = MicroBlogKey(id = "status-c", host = "test.com"),
                    text = "content-c",
                )
            val postB =
                createPost(
                    accountKey = accountKey,
                    user = userB,
                    statusKey = MicroBlogKey(id = "status-b", host = "test.com"),
                    text = "content-b",
                    quote = listOf(postC),
                )
            val postA =
                createPost(
                    accountKey = accountKey,
                    user = userA,
                    statusKey = MicroBlogKey(id = "status-a", host = "test.com"),
                    text = "content-a",
                ).copy(
                    internalRepost = postB,
                    message =
                        UiTimelineV2.Message(
                            user = userA,
                            statusKey = MicroBlogKey(id = "status-a", host = "test.com"),
                            icon = dev.dimension.flare.ui.model.UiIcon.Retweet,
                            type =
                                UiTimelineV2.Message.Type.Localized(
                                    UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                                ),
                            createdAt = Clock.System.now().toUi(),
                            clickEvent = ClickEvent.Noop,
                            accountType = AccountType.Specific(accountKey),
                        ),
                )

            val mapped = TimelinePagingMapper.toDb(postA, pagingKey = "home")
            val retweetRefs = mapped.status.references.filter { it.reference.referenceType == ReferenceType.Retweet }
            val quoteRefs = mapped.status.references.filter { it.reference.referenceType == ReferenceType.Quote }
            assertEquals(1, retweetRefs.size)
            assertEquals(postB.statusKey, retweetRefs.first().reference.referenceStatusKey)
            assertEquals(1, quoteRefs.size)
            assertEquals(postC.statusKey, quoteRefs.first().reference.referenceStatusKey)

            saveToDatabase(db, listOf(mapped))
            val savedA = db.statusDao().get(postA.statusKey, AccountType.Specific(accountKey)).first()
            val savedB = db.statusDao().get(postB.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedA)
            assertNotNull(savedB)

            val ui = TimelinePagingMapper.toUi(mapped, pagingKey = "home", useDbKeyInItemKey = false)
            val rendered = assertIs<UiTimelineV2.Post>(ui)
            val repost = assertNotNull(rendered.internalRepost)

            assertEquals(postA.statusKey, rendered.statusKey)
            assertEquals("content-b", rendered.content.raw)
            assertEquals("content-b", repost.content.raw)
            assertEquals(postB.statusKey, repost.statusKey)
            assertEquals(1, rendered.quote.size)
            assertEquals(
                "content-c",
                rendered.quote
                    .first()
                    .content.raw,
            )
            assertEquals(1, repost.quote.size)
            assertEquals(
                "content-c",
                repost.quote
                    .first()
                    .content.raw,
            )
            assertEquals(postC.statusKey, repost.quote.first().statusKey)
        }

    @Test
    fun databaseRoundTripKeepsQuoteOnInternalRepostForRetweetWrapper() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val wrapperUser = createUser(MicroBlogKey(id = "wrapper-user-quote", host = "test.com"), "Wrapper User")
            val repostUser = createUser(MicroBlogKey(id = "repost-user-quote", host = "test.com"), "Repost User")
            val quoteUser = createUser(MicroBlogKey(id = "quote-user", host = "test.com"), "Quote User")

            val quotePost =
                createPost(
                    accountKey = accountKey,
                    user = quoteUser,
                    statusKey = MicroBlogKey(id = "quote-status", host = "test.com"),
                    text = "quoted content",
                )
            val repostPost =
                createPost(
                    accountKey = accountKey,
                    user = repostUser,
                    statusKey = MicroBlogKey(id = "repost-status-without-quote", host = "test.com"),
                    text = "repost content",
                    quote = listOf(quotePost),
                )
            val repostMessage =
                UiTimelineV2.Message(
                    user = wrapperUser,
                    statusKey = MicroBlogKey(id = "wrapper-status-with-quote", host = "test.com"),
                    icon = dev.dimension.flare.ui.model.UiIcon.Retweet,
                    type =
                        UiTimelineV2.Message.Type.Localized(
                            UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                        ),
                    createdAt = Clock.System.now().toUi(),
                    clickEvent = ClickEvent.Noop,
                    accountType = AccountType.Specific(accountKey),
                )
            val wrapperPost =
                createPost(
                    accountKey = accountKey,
                    user = wrapperUser,
                    statusKey = MicroBlogKey(id = "wrapper-status-with-quote", host = "test.com"),
                    text = "wrapper content",
                ).copy(
                    message = repostMessage,
                    internalRepost = repostPost,
                )

            val mapped = TimelinePagingMapper.toDb(wrapperPost, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page = assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(refreshResult)
            val dbItem =
                assertNotNull(
                    page.data.firstOrNull {
                        it.status.status.data.statusKey == wrapperPost.statusKey
                    },
                )
            val rendered =
                assertIs<UiTimelineV2.Post>(
                    TimelinePagingMapper.toUi(
                        dbItem,
                        pagingKey = "home",
                        useDbKeyInItemKey = false,
                    ),
                )
            val internalRepost = assertNotNull(rendered.internalRepost)

            assertEquals(wrapperPost.statusKey, rendered.statusKey)
            assertEquals(repostPost.content.raw, rendered.content.raw)
            assertEquals(repostPost.statusKey, internalRepost.statusKey)
            assertEquals(1, internalRepost.quote.size)
            assertEquals(quotePost.statusKey, internalRepost.quote.first().statusKey)
            assertEquals(
                "quoted content",
                internalRepost.quote
                    .first()
                    .content.raw,
            )
        }

    @Test
    fun quoteAndRetweetTogetherKeepsRetweetMessageOnSharedStatus() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "x.com")
            val originalUser = createUser(MicroBlogKey(id = "u-original", host = "x.com"), "Original")
            val wrapperUser = createUser(MicroBlogKey(id = "u-wrapper", host = "x.com"), "Wrapper")
            val original =
                createPost(
                    accountKey = accountKey,
                    user = originalUser,
                    statusKey = MicroBlogKey(id = "fake-original-shared", host = "x.com"),
                    text = "original content",
                )
            val quoteWrapper =
                createPost(
                    accountKey = accountKey,
                    user = wrapperUser,
                    statusKey = MicroBlogKey(id = "fake-quote-wrapper", host = "x.com"),
                    text = "quote wrapper",
                    quote = listOf(original),
                )
            val retweetMessage =
                UiTimelineV2.Message(
                    user = wrapperUser,
                    statusKey = MicroBlogKey(id = "fake-retweet-wrapper", host = "x.com"),
                    icon = dev.dimension.flare.ui.model.UiIcon.Retweet,
                    type =
                        UiTimelineV2.Message.Type.Localized(
                            UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                        ),
                    createdAt = Clock.System.now().toUi(),
                    clickEvent = ClickEvent.Noop,
                    accountType = AccountType.Specific(accountKey),
                )
            val retweetItem =
                original.copy(
                    statusKey = retweetMessage.statusKey,
                    message = retweetMessage,
                )

            val items =
                listOf(
                    TimelinePagingMapper.toDb(quoteWrapper, pagingKey = "home"),
                    TimelinePagingMapper.toDb(retweetItem, pagingKey = "home"),
                )
            saveToDatabase(db, items)

            val savedRetweet =
                db
                    .statusDao()
                    .get(
                        retweetMessage.statusKey,
                        AccountType.Specific(accountKey),
                    ).first()
            val savedPost = assertIs<UiTimelineV2.Post>(assertNotNull(savedRetweet).content)
            val savedMessage = assertNotNull(savedPost.message)
            val savedType = assertIs<UiTimelineV2.Message.Type.Localized>(savedMessage.type)
            assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, savedType.data)
            assertEquals(retweetMessage.statusKey, savedPost.statusKey)
        }

    @Test
    fun detailRefreshDoesNotRemoveExistingRetweetMessage() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "x.com")
            val originalUser = createUser(MicroBlogKey(id = "u-original", host = "x.com"), "Original")
            val wrapperUser = createUser(MicroBlogKey(id = "u-wrapper", host = "x.com"), "Wrapper")
            val statusKey = MicroBlogKey(id = "fake-original-detail", host = "x.com")
            val original =
                createPost(
                    accountKey = accountKey,
                    user = originalUser,
                    statusKey = statusKey,
                    text = "original content",
                )
            val retweetMessage =
                UiTimelineV2.Message(
                    user = wrapperUser,
                    statusKey = MicroBlogKey(id = "fake-retweet-detail-wrapper", host = "x.com"),
                    icon = dev.dimension.flare.ui.model.UiIcon.Retweet,
                    type =
                        UiTimelineV2.Message.Type.Localized(
                            UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                        ),
                    createdAt = Clock.System.now().toUi(),
                    clickEvent = ClickEvent.Noop,
                    accountType = AccountType.Specific(accountKey),
                )
            val homeRetweetView =
                original.copy(
                    statusKey = retweetMessage.statusKey,
                    message = retweetMessage,
                )
            val detailView = original.copy(message = null)

            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(homeRetweetView, pagingKey = "home")))
            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(detailView, pagingKey = "post_only_$statusKey")))

            val saved =
                db.statusDao().get(retweetMessage.statusKey, AccountType.Specific(accountKey)).first()
            val savedPost = assertIs<UiTimelineV2.Post>(assertNotNull(saved).content)
            val savedMessage = assertNotNull(savedPost.message)
            val savedType = assertIs<UiTimelineV2.Message.Type.Localized>(savedMessage.type)
            assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, savedType.data)
        }

    @Test
    fun richerUserSurvivesLaterPartialUpdate() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "bsky.social")
            val userKey = MicroBlogKey(id = "did:plc:test-user", host = "bsky.social")
            val detailedUser =
                createUser(userKey, "Detailed").copy(
                    platformType = dev.dimension.flare.model.PlatformType.Bluesky,
                    banner = "https://bsky.social/banner.png",
                    description = "full profile".toUiPlainText(),
                    matrices = UiProfile.Matrices(fansCount = 12, followsCount = 34, statusesCount = 56),
                )
            val partialUser =
                createUser(userKey, "Partial").copy(
                    platformType = dev.dimension.flare.model.PlatformType.Bluesky,
                    banner = null,
                    description = null,
                    matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0),
                )

            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(
                        createPost(
                            accountKey = accountKey,
                            user = detailedUser,
                            statusKey = MicroBlogKey(id = "status-detailed", host = "bsky.social"),
                            text = "detailed",
                        ),
                        pagingKey = "home",
                    ),
                ),
            )
            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(
                        createPost(
                            accountKey = accountKey,
                            user = partialUser,
                            statusKey = MicroBlogKey(id = "status-partial", host = "bsky.social"),
                            text = "partial",
                        ),
                        pagingKey = "home",
                    ),
                ),
            )

            val savedUser = db.userDao().findByKey(userKey).first()
            val savedProfile = assertNotNull(savedUser).content
            assertEquals("https://bsky.social/banner.png", savedProfile.banner)
            assertEquals("full profile", savedProfile.description?.raw)
            assertEquals(12, savedProfile.matrices.fansCount)
            assertEquals(34, savedProfile.matrices.followsCount)
            assertEquals(56, savedProfile.matrices.statusesCount)
        }

    @Test
    fun toUiDisplaysExistingLongTextTranslationInTimelineAndDetail() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-longtext", host = "test.com")
            val longText = buildString { repeat(520) { append('长') } }
            val postUser = createUser(MicroBlogKey(id = "post-user-longtext", host = "test.com"), "Post User")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey = MicroBlogKey(id = "post-status-longtext", host = "test.com"),
                    text = longText,
                )

            val mapped = TimelinePagingMapper.toDb(post, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))
            db.translationDao().insert(
                DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey =
                        mapped.status.status.data
                            .translationEntityKey(),
                    targetLanguage = Locale.language,
                    sourceHash = post.translationPayload()!!.sourceHash(),
                    status = TranslationStatus.Completed,
                    payload = TranslationPayload(content = "长文译文".toUiPlainText()),
                    updatedAt = 1L,
                ),
            )

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page = assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(refreshResult)
            val dbItem = assertNotNull(page.data.firstOrNull())

            val timelineUi =
                TimelinePagingMapper.toUi(
                    item = dbItem,
                    pagingKey = "home",
                    useDbKeyInItemKey = false,
                    translationDisplayOptions =
                        TranslationDisplayOptions(
                            translationEnabled = true,
                            autoDisplayEnabled = true,
                        ),
                )
            val detailUi =
                TimelinePagingMapper.toUi(
                    item = dbItem,
                    pagingKey = "post_only_${post.statusKey}",
                    useDbKeyInItemKey = false,
                    translationDisplayOptions =
                        TranslationDisplayOptions(
                            translationEnabled = true,
                            autoDisplayEnabled = true,
                        ),
                )

            assertEquals("长文译文", assertIs<UiTimelineV2.Post>(timelineUi).content.raw)
            assertEquals("长文译文", assertIs<UiTimelineV2.Post>(detailUi).content.raw)
        }

    @Test
    fun toUiMarksPendingTranslationAsTranslating() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-pending", host = "test.com")
            val postUser = createUser(MicroBlogKey(id = "post-user-pending", host = "test.com"), "Post User")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey = MicroBlogKey(id = "post-status-pending", host = "test.com"),
                    text = "pending source",
                )

            val mapped = TimelinePagingMapper.toDb(post, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))
            db.translationDao().insert(
                DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey =
                        mapped.status.status.data
                            .translationEntityKey(),
                    targetLanguage = Locale.language,
                    sourceHash = post.translationPayload()!!.sourceHash(),
                    status = TranslationStatus.Pending,
                    payload = null,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page = assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(refreshResult)
            val dbItem = assertNotNull(page.data.firstOrNull())

            val timelineUi =
                assertIs<UiTimelineV2.Post>(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        useDbKeyInItemKey = false,
                        translationDisplayOptions =
                            TranslationDisplayOptions(
                                translationEnabled = true,
                                autoDisplayEnabled = true,
                            ),
                    ),
                )

            assertEquals("pending source", timelineUi.content.raw)
            assertEquals(TranslationDisplayState.Translating, timelineUi.translationDisplayState)
        }

    @Test
    fun toUiPrependsRetryTranslationToMoreMenuWhenTranslationFailed() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-failed", host = "test.com")
            val postUser = createUser(MicroBlogKey(id = "post-user-failed", host = "test.com"), "Post User")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey = MicroBlogKey(id = "post-status-failed", host = "test.com"),
                    text = "failed source",
                ).copy(
                    actions =
                        persistentListOf(
                            ActionMenu.Group(
                                displayItem =
                                    ActionMenu.Item(
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                                        clickEvent = ClickEvent.Noop,
                                    ),
                                actions =
                                    persistentListOf(
                                        ActionMenu.Item(
                                            text = ActionMenu.Item.Text.Raw("Existing action"),
                                            clickEvent = ClickEvent.Noop,
                                        ),
                                    ),
                            ),
                        ),
                )

            val mapped = TimelinePagingMapper.toDb(post, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))
            db.translationDao().insert(
                DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey =
                        mapped.status.status.data
                            .translationEntityKey(),
                    targetLanguage = Locale.language,
                    sourceHash = post.translationPayload()!!.sourceHash(),
                    status = TranslationStatus.Failed,
                    payload = null,
                    updatedAt = 1L,
                ),
            )

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page = assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(refreshResult)
            val dbItem = assertNotNull(page.data.firstOrNull())

            val timelineUi =
                assertIs<UiTimelineV2.Post>(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        useDbKeyInItemKey = false,
                        translationDisplayOptions =
                            TranslationDisplayOptions(
                                translationEnabled = true,
                                autoDisplayEnabled = true,
                            ),
                    ),
                )

            assertEquals(TranslationDisplayState.Failed, timelineUi.translationDisplayState)
            val moreAction = assertIs<ActionMenu.Group>(timelineUi.actions.first())
            val retryAction = assertIs<ActionMenu.Item>(moreAction.actions.first())
            val retryText = assertIs<ActionMenu.Item.Text.Localized>(retryAction.text)
            assertEquals(ActionMenu.Item.Text.Localized.Type.RetryTranslation, retryText.type)
            assertNull(retryAction.icon)
        }

    @Test
    fun toUiPrependsShowOriginalWhenTranslatedContentIsDisplayed() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-translated", host = "test.com")
            val postUser = createUser(MicroBlogKey(id = "post-user-translated", host = "test.com"), "Post User")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey = MicroBlogKey(id = "post-status-translated", host = "test.com"),
                    text = "source content",
                ).copy(
                    actions =
                        persistentListOf(
                            ActionMenu.Group(
                                displayItem =
                                    ActionMenu.Item(
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                                        clickEvent = ClickEvent.Noop,
                                    ),
                                actions = persistentListOf(),
                            ),
                        ),
                )

            val mapped = TimelinePagingMapper.toDb(post, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))
            db.translationDao().insert(
                DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey =
                        mapped.status.status.data
                            .translationEntityKey(),
                    targetLanguage = Locale.language,
                    sourceHash = post.translationPayload()!!.sourceHash(),
                    status = TranslationStatus.Completed,
                    payload = TranslationPayload(content = "translated content".toUiPlainText()),
                    updatedAt = 1L,
                ),
            )

            val dbItem =
                assertNotNull(
                    (
                        assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(
                            TestPager(config = PagingConfig(pageSize = 20), db.pagingTimelineDao().getPagingSource("home")).refresh(),
                        )
                    ).data.firstOrNull(),
                )

            val timelineUi =
                assertIs<UiTimelineV2.Post>(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        useDbKeyInItemKey = false,
                        translationDisplayOptions =
                            TranslationDisplayOptions(
                                translationEnabled = true,
                                autoDisplayEnabled = true,
                            ),
                    ),
                )

            assertEquals("translated content", timelineUi.content.raw)
            val moreAction = assertIs<ActionMenu.Group>(timelineUi.actions.first())
            val firstAction = assertIs<ActionMenu.Item>(moreAction.actions.first())
            assertEquals(
                ActionMenu.Item.Text.Localized.Type.ShowOriginal,
                assertIs<ActionMenu.Item.Text.Localized>(firstAction.text).type,
            )
        }

    @Test
    fun toUiPrependsTranslateWhenOriginalModeIsForced() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-original", host = "test.com")
            val postUser = createUser(MicroBlogKey(id = "post-user-original", host = "test.com"), "Post User")
            val statusKey = MicroBlogKey(id = "post-status-original", host = "test.com")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey = statusKey,
                    text = "source content",
                ).copy(
                    actions =
                        persistentListOf(
                            ActionMenu.Group(
                                displayItem =
                                    ActionMenu.Item(
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                                        clickEvent = ClickEvent.Noop,
                                    ),
                                actions = persistentListOf(),
                            ),
                        ),
                )

            val mapped = TimelinePagingMapper.toDb(post, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))
            db.translationDao().insert(
                DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey =
                        mapped.status.status.data
                            .translationEntityKey(),
                    targetLanguage = Locale.language,
                    sourceHash = post.translationPayload()!!.sourceHash(),
                    status = TranslationStatus.Completed,
                    displayMode = TranslationDisplayMode.Original,
                    payload = TranslationPayload(content = "translated content".toUiPlainText()),
                    updatedAt = 1L,
                ),
            )

            val dbItem =
                assertNotNull(
                    (
                        assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(
                            TestPager(config = PagingConfig(pageSize = 20), db.pagingTimelineDao().getPagingSource("home")).refresh(),
                        )
                    ).data.firstOrNull(),
                )

            val timelineUi =
                assertIs<UiTimelineV2.Post>(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        useDbKeyInItemKey = false,
                        translationDisplayOptions =
                            TranslationDisplayOptions(
                                translationEnabled = true,
                                autoDisplayEnabled = true,
                            ),
                    ),
                )

            assertEquals("source content", timelineUi.content.raw)
            val moreAction = assertIs<ActionMenu.Group>(timelineUi.actions.first())
            val firstAction = assertIs<ActionMenu.Item>(moreAction.actions.first())
            assertEquals(
                ActionMenu.Item.Text.Localized.Type.Translate,
                assertIs<ActionMenu.Item.Text.Localized>(firstAction.text).type,
            )
        }

    @Test
    fun toUiStillPrependsTranslateWhenPreTranslationDisplayIsDisabled() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-pretranslation-off", host = "test.com")
            val postUser = createUser(MicroBlogKey(id = "post-user-pretranslation-off", host = "test.com"), "Post User")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey = MicroBlogKey(id = "post-status-pretranslation-off", host = "test.com"),
                    text = "source content",
                ).copy(
                    actions =
                        persistentListOf(
                            ActionMenu.Group(
                                displayItem =
                                    ActionMenu.Item(
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                                        clickEvent = ClickEvent.Noop,
                                    ),
                                actions = persistentListOf(),
                            ),
                        ),
                )

            val mapped = TimelinePagingMapper.toDb(post, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))
            db.translationDao().insert(
                DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey =
                        mapped.status.status.data
                            .translationEntityKey(),
                    targetLanguage = Locale.language,
                    sourceHash = post.translationPayload()!!.sourceHash(),
                    status = TranslationStatus.Completed,
                    payload = TranslationPayload(content = "translated content".toUiPlainText()),
                    updatedAt = 1L,
                ),
            )

            val dbItem =
                assertNotNull(
                    (
                        assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(
                            TestPager(config = PagingConfig(pageSize = 20), db.pagingTimelineDao().getPagingSource("home")).refresh(),
                        )
                    ).data.firstOrNull(),
                )

            val timelineUi =
                assertIs<UiTimelineV2.Post>(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        useDbKeyInItemKey = false,
                        translationDisplayOptions =
                            TranslationDisplayOptions(
                                translationEnabled = true,
                                autoDisplayEnabled = false,
                            ),
                    ),
                )

            assertEquals("source content", timelineUi.content.raw)
            val moreAction = assertIs<ActionMenu.Group>(timelineUi.actions.first())
            val firstAction = assertIs<ActionMenu.Item>(moreAction.actions.first())
            assertEquals(
                ActionMenu.Item.Text.Localized.Type.Translate,
                assertIs<ActionMenu.Item.Text.Localized>(firstAction.text).type,
            )
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
            platformType = dev.dimension.flare.model.PlatformType.Mastodon,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0, platformFansCount = "0"),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private fun createPost(
        accountKey: MicroBlogKey,
        user: UiProfile,
        statusKey: MicroBlogKey,
        text: String,
        quote: List<UiTimelineV2.Post> = emptyList(),
        parents: List<UiTimelineV2.Post> = emptyList(),
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            message = null,
            platformType = dev.dimension.flare.model.PlatformType.Mastodon,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = user,
            quote = quote.toPersistentList(),
            content = text.toUiPlainText(),
            actions = persistentListOf(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            references = persistentListOf(),
            parents = parents.toPersistentList(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(accountKey),
        )
}
