package dev.dimension.flare.data.database.cache.mapper

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbStatusReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.database.cache.model.translationEntityKey
import dev.dimension.flare.data.database.cache.model.translationPayload
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageCache
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.nostr.bech32PublicKey
import dev.dimension.flare.data.translation.PreTranslationStoreSupport
import dev.dimension.flare.data.translation.cacheKey
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.toUiImage
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

private const val NOSTR_TEST_HOST = "nostr"

@OptIn(ExperimentalCoroutinesApi::class)
class MicroblogTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private val googleTranslationProviderCacheKey =
        AppSettings.TranslateConfig.Provider.GoogleWeb
            .cacheKey()
    private val aiTranslationProviderCacheKey =
        AppSettings.TranslateConfig.Provider.AI
            .cacheKey()

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
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

    private fun translationDisplayOptions(
        translationEnabled: Boolean = true,
        autoDisplayEnabled: Boolean = true,
        providerCacheKey: String = googleTranslationProviderCacheKey,
    ) = TranslationDisplayOptions(
        translationEnabled = translationEnabled,
        autoDisplayEnabled = autoDisplayEnabled,
        providerCacheKey = providerCacheKey,
    )

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

            val savedStatus =
                db.statusDao().get(post.statusKey, AccountType.Specific(accountKey)).first()
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
            val user =
                createUser(
                    MicroBlogKey(id = "status-update-user", host = "test.com"),
                    "Status User",
                )

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

            val savedStatus =
                db.statusDao().get(statusKey, AccountType.Specific(accountKey)).first()
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

            val mainUser =
                createUser(MicroBlogKey(id = "main-user", host = "test.com"), "Main User")
            val mainPost =
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = mainUser,
                            statusKey = MicroBlogKey(id = "main-status", host = "test.com"),
                            text = "main status",
                        ),
                    inlineParents = listOf(refPost),
                )

            val timelineItem = TimelinePagingMapper.toDb(mainPost, pagingKey = "home")
            saveToDatabase(db, listOf(timelineItem))

            val savedMainStatus =
                db.statusDao().get(mainPost.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedMainStatus)
            val savedMainPost = rootPostOf(savedMainStatus.content)
            assertEquals(1, savedMainPost.references.size)
            assertEquals(ReferenceType.Reply, savedMainPost.references.first().type)
            assertEquals(refPost.statusKey, savedMainPost.references.first().statusKey)
            val savedRefStatus =
                db.statusDao().get(refPost.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedRefStatus)

            val savedReferences =
                db.statusReferenceDao().getByStatusId(
                    DbStatus.createId(AccountType.Specific(accountKey), mainPost.statusKey),
                )
            assertEquals(1, savedReferences.size)
            assertEquals(
                DbStatus.createId(AccountType.Specific(accountKey), refPost.statusKey),
                savedReferences.first().referenceStatusId,
            )
        }

    @Test
    fun toUiResolvesQuoteNestedInsideParentReference() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val childUser = createUser(MicroBlogKey(id = "child-user", host = "test.com"), "Child User")
            val parentUser = createUser(MicroBlogKey(id = "parent-user", host = "test.com"), "Parent User")
            val quotedUser = createUser(MicroBlogKey(id = "quoted-user", host = "test.com"), "Quoted User")

            val quotedPost =
                createPost(
                    accountKey = accountKey,
                    user = quotedUser,
                    statusKey = MicroBlogKey(id = "quoted-status", host = "test.com"),
                    text = "quoted status",
                )
            val parentPost =
                createPost(
                    accountKey = accountKey,
                    user = parentUser,
                    statusKey = MicroBlogKey(id = "parent-status", host = "test.com"),
                    text = "parent status",
                )
            val childPost =
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = childUser,
                            statusKey = MicroBlogKey(id = "child-status", host = "test.com"),
                            text = "child status",
                        ),
                    inlineParents = listOf(parentPost),
                    quotes = listOf(quotedPost),
                )

            val mapped = TimelinePagingMapper.toDb(childPost, pagingKey = "home")
            val rendered =
                TimelinePagingMapper.toUi(mapped, pagingKey = "home", translationDisplayOptions())
                    as UiTimelineV2.TimelinePostItem
            val renderedParent = rendered.presentation.inlineParents.single()

            assertEquals(parentPost.statusKey, renderedParent.statusKey)
            assertEquals(1, rendered.presentation.quotes.size)
            assertEquals(
                quotedPost.statusKey,
                rendered.presentation.quotes
                    .first()
                    .statusKey,
            )
            assertEquals(
                "quoted status",
                rendered.presentation.quotes
                    .first()
                    .content.original.raw,
            )
        }

    @Test
    fun saveToDatabaseStillPersistsUsersForPostUserAndUserListTimeline() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val firstUser = createUser(MicroBlogKey(id = "user-1", host = "test.com"), "First User")
            val secondUser =
                createUser(MicroBlogKey(id = "user-2", host = "test.com"), "Second User")
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

            val mainUser =
                createUser(MicroBlogKey(id = "main-user", host = "test.com"), "Main User")
            val withRef =
                createPost(
                    accountKey = accountKey,
                    user = mainUser,
                    statusKey = MicroBlogKey(id = "main-status", host = "test.com"),
                    text = "main status",
                    parents = persistentListOf(refPost),
                )

            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withRef, pagingKey = "home")))
            assertEquals(
                1,
                db
                    .statusReferenceDao()
                    .getByStatusId(DbStatus.createId(AccountType.Specific(accountKey), withRef.statusKey))
                    .size,
            )

            val withoutRef = withRef.copy(references = persistentListOf())
            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withoutRef, pagingKey = "home")))

            val refsAfter =
                db.statusReferenceDao().getByStatusId(
                    DbStatus.createId(AccountType.Specific(accountKey), withRef.statusKey),
                )
            assertEquals(0, refsAfter.size)

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            assertPage(refreshResult)
        }

    @Test
    fun timelineOffsetPageSkipsTimelineRowsWithoutStatus() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val user = createUser(MicroBlogKey(id = "user", host = "test.com"), "User")
            val first =
                TimelinePagingMapper.toDb(
                    createPost(
                        accountKey = accountKey,
                        user = user,
                        statusKey = MicroBlogKey(id = "first-status", host = "test.com"),
                        text = "first",
                    ),
                    pagingKey = "home",
                    sortId = 10,
                )
            val second =
                TimelinePagingMapper.toDb(
                    createPost(
                        accountKey = accountKey,
                        user = user,
                        statusKey = MicroBlogKey(id = "second-status", host = "test.com"),
                        text = "second",
                    ),
                    pagingKey = "home",
                    sortId = 30,
                )

            saveToDatabase(db, listOf(first, second))
            db.pagingTimelineDao().insertAll(
                listOf(
                    DbPagingTimeline(
                        pagingKey = "home",
                        statusId = "missing-status",
                        sortId = 20,
                    ),
                ),
            )

            val loader = TimelineDbPageLoader(db, "home", TimelineDbPageCache())

            assertEquals(
                listOf(first.timeline.statusId),
                loader.load(offset = 0, limit = 1).map { it.status.status.data.id },
            )
            assertEquals(
                listOf(second.timeline.statusId),
                loader.load(offset = 1, limit = 1).map { it.status.status.data.id },
            )
            assertEquals(
                emptyList(),
                loader.load(offset = 2, limit = 1).map { it.status.status.data.id },
            )
        }

    @Test
    fun staleQuoteReferencesAreRemovedWhenSubsequentInsertHasNoQuoteReferences() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")

            val quoteUser =
                createUser(MicroBlogKey(id = "quote-user", host = "test.com"), "Quote User")
            val quotePost =
                createPost(
                    accountKey = accountKey,
                    user = quoteUser,
                    statusKey = MicroBlogKey(id = "quote-status", host = "test.com"),
                    text = "quote status",
                )

            val mainUser =
                createUser(MicroBlogKey(id = "main-user-quote", host = "test.com"), "Main User")
            val withQuote =
                createPost(
                    accountKey = accountKey,
                    user = mainUser,
                    statusKey = MicroBlogKey(id = "main-status-quote", host = "test.com"),
                    text = "main status",
                    quote = persistentListOf(quotePost),
                )

            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withQuote, pagingKey = "home")))
            assertEquals(
                1,
                db
                    .statusReferenceDao()
                    .getByStatusId(DbStatus.createId(AccountType.Specific(accountKey), withQuote.statusKey))
                    .size,
            )

            val withoutQuote = withQuote.copy(references = persistentListOf())
            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withoutQuote, pagingKey = "home")))

            val refsAfter =
                db.statusReferenceDao().getByStatusId(
                    DbStatus.createId(AccountType.Specific(accountKey), withQuote.statusKey),
                )
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

            val mainUser =
                createUser(MicroBlogKey(id = "main-user", host = "test.com"), "Main User")
            val withParents =
                createPost(
                    accountKey = accountKey,
                    user = mainUser,
                    statusKey = MicroBlogKey(id = "main-status-2", host = "test.com"),
                    text = "main status",
                    parents = persistentListOf(refPost),
                )

            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(withParents, pagingKey = "home")))
            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(
                        withParents,
                        pagingKey = "post_only_${withParents.statusKey}",
                    ),
                ),
            )

            val saved =
                db.statusDao().get(withParents.statusKey, AccountType.Specific(accountKey)).first()
            val savedStatus = assertNotNull(saved)
            val savedPost = rootPostOf(savedStatus.content)
            assertEquals(1, savedPost.references.size)
            assertEquals(refPost.statusKey, savedPost.references.first().statusKey)
            assertEquals(savedPost.renderHash, savedStatus.renderHash)
        }

    @Test
    fun toDbStoresRenderHashForSanitizedContent() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-render-hash", host = "test.com")
            val user = createUser(MicroBlogKey(id = "user-render-hash", host = "test.com"), "User")
            val parent =
                createPost(
                    accountKey = accountKey,
                    user = user,
                    statusKey = MicroBlogKey(id = "parent-render-hash", host = "test.com"),
                    text = "parent",
                )
            val rootPost =
                createPost(
                    accountKey = accountKey,
                    user = user,
                    statusKey = MicroBlogKey(id = "root-render-hash", host = "test.com"),
                    text = "root",
                )
            val post = timelinePostItem(rootPost, inlineParents = listOf(parent))

            val savedStatus =
                TimelinePagingMapper
                    .toDb(post, pagingKey = "home")
                    .status.status.data
            val savedPost = rootPostOf(savedStatus.content)

            assertEquals(1, savedPost.references.size)
            assertEquals(savedPost.renderHash, savedStatus.renderHash)
            assertNotEquals(post.renderHash, savedStatus.renderHash)
        }

    @Test
    fun toDbMapsReplyReference() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val rootUser =
                createUser(MicroBlogKey(id = "root-user", host = "test.com"), "Root User")
            val parentUser =
                createUser(MicroBlogKey(id = "parent-user", host = "test.com"), "Parent User")
            val parentPost =
                createPost(
                    accountKey = accountKey,
                    user = parentUser,
                    statusKey = MicroBlogKey(id = "parent-status", host = "test.com"),
                    text = "parent",
                )
            val rootPost =
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = rootUser,
                            statusKey = MicroBlogKey(id = "root-status", host = "test.com"),
                            text = "root",
                        ),
                    inlineParents = listOf(parentPost),
                )

            val mapped = TimelinePagingMapper.toDb(rootPost, pagingKey = "home")
            assertEquals(mapped.status.status.data.id, mapped.timeline.statusId)
            assertEquals(1, mapped.status.references.size)
            val reference =
                mapped.status.references
                    .first()
                    .reference
            assertEquals(ReferenceType.Reply, reference.referenceType)
            assertEquals(
                DbStatus.createId(AccountType.Specific(accountKey), rootPost.statusKey),
                reference.statusId,
            )
            assertEquals(
                DbStatus.createId(AccountType.Specific(accountKey), parentPost.statusKey),
                reference.referenceStatusId,
            )
        }

    @Test
    fun toDbMapsRetweetReferenceFromInternalRepost() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val wrapperUser =
                createUser(MicroBlogKey(id = "wrapper-user", host = "test.com"), "Wrapper User")
            val repostUser =
                createUser(MicroBlogKey(id = "repost-user", host = "test.com"), "Repost User")
            val repostPost =
                createPost(
                    accountKey = accountKey,
                    user = repostUser,
                    statusKey = MicroBlogKey(id = "repost-status", host = "test.com"),
                    text = "repost",
                )
            val wrapperPost =
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = wrapperUser,
                            statusKey = MicroBlogKey(id = "wrapper-status", host = "test.com"),
                            text = "wrapper",
                        ),
                    repost = repostPost,
                )

            val mapped = TimelinePagingMapper.toDb(wrapperPost, pagingKey = "home")
            val retweetReference =
                mapped.status.references.find { it.reference.referenceType == ReferenceType.Retweet }
            assertNotNull(retweetReference)
            assertEquals(
                DbStatus.createId(AccountType.Specific(accountKey), wrapperPost.statusKey),
                retweetReference.reference.statusId,
            )
            assertEquals(
                DbStatus.createId(AccountType.Specific(accountKey), repostPost.statusKey),
                retweetReference.reference.referenceStatusId,
            )
        }

    @Test
    fun toUiSetsExtraKeyForRootAndReferences() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val rootUser =
                createUser(MicroBlogKey(id = "root-user", host = "test.com"), "Root User")
            val parentUser =
                createUser(MicroBlogKey(id = "parent-user", host = "test.com"), "Parent User")
            val parentPost =
                createPost(
                    accountKey = accountKey,
                    user = parentUser,
                    statusKey = MicroBlogKey(id = "parent-status", host = "test.com"),
                    text = "parent",
                )
            val rootPost =
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = rootUser,
                            statusKey = MicroBlogKey(id = "root-status", host = "test.com"),
                            text = "root",
                        ),
                    inlineParents = listOf(parentPost),
                )

            val mapped = TimelinePagingMapper.toDb(rootPost, pagingKey = "home")
            val ui =
                TimelinePagingMapper.toUi(
                    mapped,
                    pagingKey = "home",
                    translationDisplayOptions = translationDisplayOptions(),
                )
            val post = assertIs<UiTimelineV2.TimelinePostItem>(ui)
            assertEquals("home_${mapped.status.status.data.id}", post.itemKey)
            assertEquals(1, post.presentation.inlineParents.size)
            assertEquals(
                "home_${mapped.presentationReferences.first().status?.data?.id}",
                post.presentation.inlineParents
                    .first()
                    .itemKey,
            )
            assertEquals(
                parentPost.statusKey,
                post.presentation.inlineParents
                    .first()
                    .statusKey,
            )
        }

    @Test
    fun toUiUsesCompletedTranslationForRootAndReplyReference() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val rootUser =
                createUser(
                    MicroBlogKey(id = "root-user-translated", host = "test.com"),
                    "Root User",
                )
            val parentUser =
                createUser(
                    MicroBlogKey(id = "parent-user-translated", host = "test.com"),
                    "Parent User",
                )
            val parentPost =
                createPost(
                    accountKey = accountKey,
                    user = parentUser,
                    statusKey = MicroBlogKey(id = "parent-status-translated", host = "test.com"),
                    text = "parent original",
                )
            val rootPost =
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = rootUser,
                            statusKey = MicroBlogKey(id = "root-status-translated", host = "test.com"),
                            text = "root original",
                        ),
                    inlineParents = listOf(parentPost),
                )

            val mapped = TimelinePagingMapper.toDb(rootPost, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))
            val savedParentStatus =
                assertNotNull(
                    db.statusDao().get(parentPost.statusKey, AccountType.Specific(accountKey)).first(),
                )
            db.translationDao().insertAll(
                listOf(
                    DbTranslation(
                        entityType = TranslationEntityType.Status,
                        entityKey =
                            mapped.status.status.data
                                .translationEntityKey(),
                        targetLanguage = Locale.language,
                        sourceHash =
                            rootPost
                                .translationPayload()!!
                                .sourceHash(googleTranslationProviderCacheKey),
                        status = TranslationStatus.Completed,
                        payload = TranslationPayload(content = "根帖子".toUiPlainText()),
                        updatedAt = 1L,
                    ),
                    DbTranslation(
                        entityType = TranslationEntityType.Status,
                        entityKey = savedParentStatus.translationEntityKey(),
                        targetLanguage = Locale.language,
                        sourceHash =
                            parentPost
                                .translationPayload()!!
                                .sourceHash(googleTranslationProviderCacheKey),
                        status = TranslationStatus.Completed,
                        payload = TranslationPayload(content = "父帖子".toUiPlainText()),
                        updatedAt = 1L,
                    ),
                ),
            )

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page =
                assertPage(
                    refreshResult,
                )
            val dbItem = assertNotNull(page.data.firstOrNull())

            val ui =
                TimelinePagingMapper.toUi(
                    item = dbItem,
                    pagingKey = "home",
                    translationDisplayOptions = translationDisplayOptions(),
                )
            val post = assertIs<UiTimelineV2.TimelinePostItem>(ui)

            assertEquals("root original", post.post.content.original.raw)
            assertEquals(
                "根帖子",
                post.post.content.translation
                    ?.raw,
            )
            assertEquals(1, post.presentation.inlineParents.size)
            assertEquals(
                "parent original",
                post.presentation.inlineParents
                    .first()
                    .content.original.raw,
            )
            assertEquals(
                "父帖子",
                post.presentation.inlineParents
                    .first()
                    .content.translation
                    ?.raw,
            )
        }

    @Test
    fun timelinePagingMapperKeepsPostMessageAfterRoundTrip() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val postUser =
                createUser(MicroBlogKey(id = "post-user", host = "test.com"), "Post User")
            val post =
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = postUser,
                            statusKey = MicroBlogKey(id = "post-status", host = "test.com"),
                            text = "post content",
                        ),
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
            val roundTrip =
                TimelinePagingMapper.toUi(mapped, pagingKey = "home", translationDisplayOptions())
            val rendered = assertIs<UiTimelineV2.TimelinePostItem>(roundTrip)
            val message = assertNotNull(rendered.presentation.message)
            val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)

            assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        }

    @Test
    fun toUiUsesEmbeddedUserDataWithoutReadingUserJoin() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val user =
                createUser(MicroBlogKey(id = "user-join", host = "test.com"), "Embedded User")
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
            val page =
                assertPage(
                    refreshResult,
                )
            val dbItem =
                assertNotNull(
                    page.data.firstOrNull {
                        it.status.status.data.statusKey == post.statusKey
                    },
                )

            val rendered =
                rootPostOf(
                    TimelinePagingMapper.toUi(
                        dbItem,
                        pagingKey = "home",
                        translationDisplayOptions(),
                    ),
                )
            assertEquals("Embedded User", rendered.user?.name?.raw)
        }

    @Test
    fun toUiFlattensInternalRepostButKeepsReferencePayload() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val wrapperUser =
                createUser(MicroBlogKey(id = "wrapper-user", host = "test.com"), "Wrapper User")
            val repostUser =
                createUser(MicroBlogKey(id = "repost-user", host = "test.com"), "Repost User")
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
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = wrapperUser,
                            statusKey = MicroBlogKey(id = "wrapper-status", host = "test.com"),
                            text = "wrapper content",
                        ),
                    message = repostMessage,
                    repost = repostPost,
                )

            val mapped = TimelinePagingMapper.toDb(wrapperPost, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))

            val savedWrapper =
                db.statusDao().get(wrapperPost.statusKey, AccountType.Specific(accountKey)).first()
            val savedRepost =
                db.statusDao().get(repostPost.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedWrapper)
            assertNotNull(savedRepost)
            val savedWrapperPost = rootPostOf(savedWrapper.content)
            assertEquals(1, savedWrapperPost.references.size)
            assertEquals(ReferenceType.Retweet, savedWrapperPost.references.first().type)

            val roundTrip =
                TimelinePagingMapper.toUi(mapped, pagingKey = "home", translationDisplayOptions())
            val rendered = assertIs<UiTimelineV2.TimelinePostItem>(roundTrip)
            val internalRepost = assertNotNull(rendered.presentation.repost)

            assertEquals(wrapperPost.statusKey, rendered.post.statusKey)
            assertEquals(repostPost.statusKey, internalRepost.statusKey)
            assertEquals(repostPost.content.original.raw, rendered.displayPost.content.original.raw)
            assertEquals(repostPost.user?.key, rendered.displayPost.user?.key)
            assertEquals(repostPost.content.original.raw, internalRepost.content.original.raw)

            val message = assertNotNull(rendered.presentation.message)
            val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)
            assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        }

    @Test
    fun toUiKeepsDistinctItemKeysForOriginalAndRepostWrapper() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val originalUser =
                createUser(MicroBlogKey(id = "original-user", host = "test.com"), "Original User")
            val wrapperUser =
                createUser(MicroBlogKey(id = "wrapper-user", host = "test.com"), "Wrapper User")

            val originalPost =
                createPost(
                    accountKey = accountKey,
                    user = originalUser,
                    statusKey = MicroBlogKey(id = "original-status", host = "test.com"),
                    text = "original content",
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
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = wrapperUser,
                            statusKey = MicroBlogKey(id = "wrapper-status", host = "test.com"),
                            text = "wrapper content",
                        ),
                    message = repostMessage,
                    repost = originalPost,
                )

            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(originalPost, pagingKey = "home"),
                    TimelinePagingMapper.toDb(wrapperPost, pagingKey = "home"),
                ),
            )

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page =
                assertPage(
                    refreshResult,
                )

            val originalDbItem =
                assertNotNull(
                    page.data.firstOrNull {
                        it.status.status.data.statusKey == originalPost.statusKey
                    },
                )
            val wrapperDbItem =
                assertNotNull(
                    page.data.firstOrNull {
                        it.status.status.data.statusKey == wrapperPost.statusKey
                    },
                )

            val renderedOriginal =
                assertIs<UiTimelineV2.TimelinePostItem>(
                    TimelinePagingMapper.toUi(
                        item = originalDbItem,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(),
                    ),
                )
            val renderedWrapper =
                assertIs<UiTimelineV2.TimelinePostItem>(
                    TimelinePagingMapper.toUi(
                        item = wrapperDbItem,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(),
                    ),
                )

            assertEquals(originalPost.statusKey, renderedOriginal.post.statusKey)
            assertEquals(wrapperPost.statusKey, renderedWrapper.post.statusKey)
            assertNotEquals(renderedOriginal.itemKey, renderedWrapper.itemKey)
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
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = userA,
                            statusKey = MicroBlogKey(id = "status-a", host = "test.com"),
                            text = "content-a",
                        ),
                    repost = postB,
                    quotes = listOf(postC),
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
            val retweetRefs =
                mapped.status.references.filter { it.reference.referenceType == ReferenceType.Retweet }
            val quoteRefs =
                mapped.status.references.filter { it.reference.referenceType == ReferenceType.Quote }
            assertEquals(1, retweetRefs.size)
            assertEquals(
                DbStatus.createId(AccountType.Specific(accountKey), postB.statusKey),
                retweetRefs.first().reference.referenceStatusId,
            )
            assertEquals(1, quoteRefs.size)
            assertEquals(
                DbStatus.createId(AccountType.Specific(accountKey), postC.statusKey),
                quoteRefs.first().reference.referenceStatusId,
            )

            saveToDatabase(db, listOf(mapped))
            val savedA =
                db.statusDao().get(postA.statusKey, AccountType.Specific(accountKey)).first()
            val savedB =
                db.statusDao().get(postB.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedA)
            assertNotNull(savedB)

            val ui =
                TimelinePagingMapper.toUi(mapped, pagingKey = "home", translationDisplayOptions())
            val rendered = assertIs<UiTimelineV2.TimelinePostItem>(ui)
            val repost = assertNotNull(rendered.presentation.repost)

            assertEquals(postA.statusKey, rendered.post.statusKey)
            assertEquals("content-b", rendered.displayPost.content.original.raw)
            assertEquals("content-b", repost.content.original.raw)
            assertEquals(postB.statusKey, repost.statusKey)
            assertEquals(1, rendered.presentation.quotes.size)
            assertEquals(
                "content-c",
                rendered.presentation.quotes
                    .first()
                    .content.original.raw,
            )
            assertEquals(
                postC.statusKey,
                rendered.presentation.quotes
                    .first()
                    .statusKey,
            )
        }

    @Test
    fun databaseRoundTripKeepsQuoteOnInternalRepostForRetweetWrapper() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")
            val wrapperUser =
                createUser(
                    MicroBlogKey(id = "wrapper-user-quote", host = "test.com"),
                    "Wrapper User",
                )
            val repostUser =
                createUser(MicroBlogKey(id = "repost-user-quote", host = "test.com"), "Repost User")
            val quoteUser =
                createUser(MicroBlogKey(id = "quote-user", host = "test.com"), "Quote User")

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
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = wrapperUser,
                            statusKey = MicroBlogKey(id = "wrapper-status-with-quote", host = "test.com"),
                            text = "wrapper content",
                        ),
                    message = repostMessage,
                    repost = repostPost,
                    quotes = listOf(quotePost),
                )

            val mapped = TimelinePagingMapper.toDb(wrapperPost, pagingKey = "home")
            saveToDatabase(db, listOf(mapped))

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page =
                assertPage(
                    refreshResult,
                )
            val dbItem =
                assertNotNull(
                    page.data.firstOrNull {
                        it.status.status.data.statusKey == wrapperPost.statusKey
                    },
                )
            val rendered =
                assertIs<UiTimelineV2.TimelinePostItem>(
                    TimelinePagingMapper.toUi(
                        dbItem,
                        pagingKey = "home",
                        translationDisplayOptions(),
                    ),
                )
            val internalRepost = assertNotNull(rendered.presentation.repost)

            assertEquals(wrapperPost.statusKey, rendered.post.statusKey)
            assertEquals(repostPost.content.original.raw, rendered.displayPost.content.original.raw)
            assertEquals(repostPost.statusKey, internalRepost.statusKey)
            assertEquals(1, rendered.presentation.quotes.size)
            assertEquals(
                quotePost.statusKey,
                rendered.presentation.quotes
                    .first()
                    .statusKey,
            )
            assertEquals(
                "quoted content",
                rendered.presentation.quotes
                    .first()
                    .content.original.raw,
            )
        }

    @Test
    fun quoteAndRetweetTogetherKeepsRetweetMessageOnSharedStatus() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "x.com")
            val originalUser =
                createUser(MicroBlogKey(id = "u-original", host = "x.com"), "Original")
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
                timelinePostItem(
                    post = original.copy(statusKey = retweetMessage.statusKey),
                    message = retweetMessage,
                    repost = original,
                )

            val items =
                listOf(
                    TimelinePagingMapper.toDb(quoteWrapper, pagingKey = "home"),
                    TimelinePagingMapper.toDb(retweetItem, pagingKey = "home"),
                )
            saveToDatabase(db, items)

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page =
                assertPage(
                    refreshResult,
                )
            val savedRetweet =
                assertNotNull(
                    page.data.firstOrNull {
                        it.status.status.data.statusKey == retweetMessage.statusKey
                    },
                )
            val savedPost =
                assertIs<UiTimelineV2.TimelinePostItem>(
                    TimelinePagingMapper.toUi(savedRetweet, "home", translationDisplayOptions()),
                )
            val savedMessage = assertNotNull(savedPost.presentation.message)
            val savedType = assertIs<UiTimelineV2.Message.Type.Localized>(savedMessage.type)
            assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, savedType.data)
            assertEquals(retweetMessage.statusKey, savedPost.statusKey)
        }

    @Test
    fun detailRefreshDoesNotRemoveExistingRetweetMessage() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "x.com")
            val originalUser =
                createUser(MicroBlogKey(id = "u-original", host = "x.com"), "Original")
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
                timelinePostItem(
                    post = original.copy(statusKey = retweetMessage.statusKey),
                    message = retweetMessage,
                    repost = original,
                )
            val detailView = original

            saveToDatabase(
                db,
                listOf(TimelinePagingMapper.toDb(homeRetweetView, pagingKey = "home")),
            )
            saveToDatabase(
                db,
                listOf(TimelinePagingMapper.toDb(detailView, pagingKey = "post_only_$statusKey")),
            )

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page =
                assertPage(
                    refreshResult,
                )
            val saved =
                assertNotNull(
                    page.data.firstOrNull {
                        it.status.status.data.statusKey == retweetMessage.statusKey
                    },
                )
            val savedPost =
                assertIs<UiTimelineV2.TimelinePostItem>(
                    TimelinePagingMapper.toUi(saved, "home", translationDisplayOptions()),
                )
            val savedMessage = assertNotNull(savedPost.presentation.message)
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
                    banner = "https://bsky.social/banner.png".toUiImage(),
                    description = "full profile".toUiPlainText(),
                    matrices =
                        UiProfile.Matrices(
                            fansCount = 12,
                            followsCount = 34,
                            statusesCount = 56,
                        ),
                )
            val partialUser =
                createUser(userKey, "Partial").copy(
                    platformType = dev.dimension.flare.model.PlatformType.Bluesky,
                    banner = null,
                    description = null,
                    matrices =
                        UiProfile.Matrices(
                            fansCount = 0,
                            followsCount = 0,
                            statusesCount = 0,
                        ),
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
            assertEquals("https://bsky.social/banner.png", savedProfile.banner?.url)
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
            val postUser =
                createUser(MicroBlogKey(id = "post-user-longtext", host = "test.com"), "Post User")
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
                    sourceHash =
                        post
                            .translationPayload()!!
                            .sourceHash(googleTranslationProviderCacheKey),
                    status = TranslationStatus.Completed,
                    payload = TranslationPayload(content = "长文译文".toUiPlainText()),
                    updatedAt = 1L,
                ),
            )

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page =
                assertPage(
                    refreshResult,
                )
            val dbItem = assertNotNull(page.data.firstOrNull())

            val timelineUi =
                TimelinePagingMapper.toUi(
                    item = dbItem,
                    pagingKey = "home",
                    translationDisplayOptions = translationDisplayOptions(),
                )
            val detailUi =
                TimelinePagingMapper.toUi(
                    item = dbItem,
                    pagingKey = "post_only_${post.statusKey}",
                    translationDisplayOptions = translationDisplayOptions(),
                )

            assertEquals("长文译文", rootPostOf(timelineUi).content.translation?.raw)
            assertEquals("长文译文", rootPostOf(detailUi).content.translation?.raw)
        }

    @Test
    fun toUiIgnoresCachedTranslationWhenProviderChanges() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-provider-switch", host = "test.com")
            val postUser =
                createUser(
                    MicroBlogKey(id = "post-user-provider-switch", host = "test.com"),
                    "Post User",
                )
            val post =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey = MicroBlogKey(id = "post-status-provider-switch", host = "test.com"),
                    text = "source content",
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
                    sourceHash =
                        post
                            .translationPayload()!!
                            .sourceHash(googleTranslationProviderCacheKey),
                    status = TranslationStatus.Completed,
                    payload = TranslationPayload(content = "translated content".toUiPlainText()),
                    updatedAt = 1L,
                ),
            )

            val dbItem =
                assertNotNull(
                    (
                        assertPage(
                            TestPager(
                                config = PagingConfig(pageSize = 20),
                                db.pagingTimelineDao().getPagingSource("home"),
                            ).refresh(),
                        )
                    ).data.firstOrNull(),
                )

            val timelineUi =
                rootPostOf(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(providerCacheKey = aiTranslationProviderCacheKey),
                    ),
                )

            assertEquals("source content", timelineUi.content.original.raw)
            assertEquals(TranslationDisplayState.Hidden, timelineUi.translationDisplayState)
        }

    @Test
    fun toUiMarksPendingTranslationAsTranslatingAndKeepsNoopTranslateAction() =
        runTest {
            assertInFlightTranslationKeepsNoopTranslateAction(TranslationStatus.Pending)
        }

    @Test
    fun toUiMarksTranslatingTranslationAsTranslatingAndKeepsNoopTranslateAction() =
        runTest {
            assertInFlightTranslationKeepsNoopTranslateAction(TranslationStatus.Translating)
        }

    @Test
    fun toUiPrependsRetryTranslationToMoreMenuWhenTranslationFailed() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-failed", host = "test.com")
            val postUser =
                createUser(MicroBlogKey(id = "post-user-failed", host = "test.com"), "Post User")
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
                    sourceHash =
                        post
                            .translationPayload()!!
                            .sourceHash(googleTranslationProviderCacheKey),
                    status = TranslationStatus.Failed,
                    payload = null,
                    updatedAt = 1L,
                ),
            )

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            val page =
                assertPage(
                    refreshResult,
                )
            val dbItem = assertNotNull(page.data.firstOrNull())

            val timelineUi =
                rootPostOf(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(),
                    ),
                )

            assertEquals(TranslationDisplayState.Failed, timelineUi.translationDisplayState)
            val moreAction = assertIs<ActionMenu.Group>(timelineUi.actions.first())
            val retryAction = assertIs<ActionMenu.Item>(moreAction.actions.first())
            val retryText = assertIs<ActionMenu.Item.Text.Localized>(retryAction.text)
            assertEquals(ActionMenu.Item.Text.Localized.Type.RetryTranslation, retryText.type)
            assertEquals(UiIcon.Translate, retryAction.icon)
        }

    @Test
    fun toUiPrependsTranslateWhenTranslationSkippedForExcludedLanguage() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-skipped-excluded", host = "test.com")
            val postUser =
                createUser(MicroBlogKey(id = "post-user-skipped-excluded", host = "test.com"), "Post User")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey = MicroBlogKey(id = "post-status-skipped-excluded", host = "test.com"),
                    text = "excluded source",
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
                    sourceHash = post.translationPayload()!!.sourceHash(googleTranslationProviderCacheKey),
                    status = TranslationStatus.Skipped,
                    statusReason = PreTranslationStoreSupport.SKIPPED_EXCLUDED_LANGUAGE_REASON,
                    payload = null,
                    updatedAt = 1L,
                ),
            )

            val dbItem =
                assertNotNull(
                    (
                        assertPage(
                            TestPager(
                                config = PagingConfig(pageSize = 20),
                                db.pagingTimelineDao().getPagingSource("home"),
                            ).refresh(),
                        )
                    ).data.firstOrNull(),
                )

            val timelineUi =
                rootPostOf(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(),
                    ),
                )

            val moreAction = assertIs<ActionMenu.Group>(timelineUi.actions.first())
            val firstAction = assertIs<ActionMenu.Item>(moreAction.actions.first())
            assertEquals(
                ActionMenu.Item.Text.Localized.Type.Translate,
                assertIs<ActionMenu.Item.Text.Localized>(firstAction.text).type,
            )
        }

    @Test
    fun toUiPrependsShowOriginalWhenTranslatedContentIsDisplayed() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-translated", host = "test.com")
            val postUser =
                createUser(
                    MicroBlogKey(id = "post-user-translated", host = "test.com"),
                    "Post User",
                )
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
                    sourceHash =
                        post
                            .translationPayload()!!
                            .sourceHash(googleTranslationProviderCacheKey),
                    status = TranslationStatus.Completed,
                    payload = TranslationPayload(content = "translated content".toUiPlainText()),
                    updatedAt = 1L,
                ),
            )

            val dbItem =
                assertNotNull(
                    (
                        assertPage(
                            TestPager(
                                config = PagingConfig(pageSize = 20),
                                db.pagingTimelineDao().getPagingSource("home"),
                            ).refresh(),
                        )
                    ).data.firstOrNull(),
                )

            val timelineUi =
                rootPostOf(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(),
                    ),
                )

            assertEquals("source content", timelineUi.content.original.raw)
            assertEquals("translated content", timelineUi.content.translation?.raw)
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
            val postUser =
                createUser(MicroBlogKey(id = "post-user-original", host = "test.com"), "Post User")
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
                    sourceHash =
                        post
                            .translationPayload()!!
                            .sourceHash(googleTranslationProviderCacheKey),
                    status = TranslationStatus.Completed,
                    displayMode = TranslationDisplayMode.Original,
                    payload = TranslationPayload(content = "translated content".toUiPlainText()),
                    updatedAt = 1L,
                ),
            )

            val dbItem =
                assertNotNull(
                    (
                        assertPage(
                            TestPager(
                                config = PagingConfig(pageSize = 20),
                                db.pagingTimelineDao().getPagingSource("home"),
                            ).refresh(),
                        )
                    ).data.firstOrNull(),
                )

            val timelineUi =
                rootPostOf(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(),
                    ),
                )

            assertEquals("source content", timelineUi.content.original.raw)
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
            val postUser =
                createUser(
                    MicroBlogKey(id = "post-user-pretranslation-off", host = "test.com"),
                    "Post User",
                )
            val post =
                createPost(
                    accountKey = accountKey,
                    user = postUser,
                    statusKey =
                        MicroBlogKey(
                            id = "post-status-pretranslation-off",
                            host = "test.com",
                        ),
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
                    sourceHash =
                        post
                            .translationPayload()!!
                            .sourceHash(googleTranslationProviderCacheKey),
                    status = TranslationStatus.Completed,
                    payload = TranslationPayload(content = "translated content".toUiPlainText()),
                    updatedAt = 1L,
                ),
            )

            val dbItem =
                assertNotNull(
                    (
                        assertPage(
                            TestPager(
                                config = PagingConfig(pageSize = 20),
                                db.pagingTimelineDao().getPagingSource("home"),
                            ).refresh(),
                        )
                    ).data.firstOrNull(),
                )

            val timelineUi =
                rootPostOf(
                    TimelinePagingMapper.toUi(
                        item = dbItem,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(autoDisplayEnabled = false),
                    ),
                )

            assertEquals("source content", timelineUi.content.original.raw)
            val moreAction = assertIs<ActionMenu.Group>(timelineUi.actions.first())
            val firstAction = assertIs<ActionMenu.Item>(moreAction.actions.first())
            assertEquals(
                ActionMenu.Item.Text.Localized.Type.Translate,
                assertIs<ActionMenu.Item.Text.Localized>(firstAction.text).type,
            )
        }

    @Test
    fun saveToDatabaseKeepsExistingNostrProfileWhenIncomingUsesFallbackNpub() =
        runTest {
            val accountKey = MicroBlogKey(id = "nostr-account", host = NOSTR_TEST_HOST)
            val userKey =
                MicroBlogKey(
                    id = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    host = NOSTR_TEST_HOST,
                )
            val detailedUser =
                UiProfile(
                    key = userKey,
                    handle = UiHandle(raw = "alice", host = NOSTR_TEST_HOST),
                    avatar = "https://example.com/alice.png",
                    nameInternal = "Alice".toUiPlainText(),
                    platformType = dev.dimension.flare.model.PlatformType.Nostr,
                    clickEvent = ClickEvent.Noop,
                    banner = "https://example.com/banner.png",
                    description = "hello".toUiPlainText(),
                    matrices = UiProfile.Matrices(0, 0, 0),
                    mark = persistentListOf(),
                    bottomContent = null,
                )
            val fallback = bech32PublicKey(userKey.id).take(16)
            val fallbackUser =
                UiProfile(
                    key = userKey,
                    handle = UiHandle(raw = fallback, host = NOSTR_TEST_HOST),
                    avatar = "",
                    nameInternal = fallback.toUiPlainText(),
                    platformType = dev.dimension.flare.model.PlatformType.Nostr,
                    clickEvent = ClickEvent.Noop,
                    banner = null,
                    description = null,
                    matrices = UiProfile.Matrices(0, 0, 0),
                    mark = persistentListOf(),
                    bottomContent = null,
                )

            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(
                        createPost(
                            accountKey = accountKey,
                            user = detailedUser,
                            statusKey = MicroBlogKey(id = "status-detailed", host = NOSTR_TEST_HOST),
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
                            user = fallbackUser,
                            statusKey = MicroBlogKey(id = "status-fallback", host = NOSTR_TEST_HOST),
                            text = "fallback",
                        ),
                        pagingKey = "home",
                    ),
                ),
            )

            val savedUser = db.userDao().findByKey(userKey).first()
            val savedProfile = assertNotNull(savedUser).content
            assertEquals("alice", savedProfile.handle.raw)
            assertEquals("Alice", savedProfile.name.raw)
            assertEquals("https://example.com/alice.png", savedProfile.avatar?.url)
            assertEquals("https://example.com/banner.png", savedProfile.banner?.url)
            assertEquals("hello", savedProfile.description?.raw)
        }

    @Test
    fun cachedTimelineMapperDoesNotOverflowWhenReplyReferenceChainIsVeryLong() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-long-cache", host = "test.com")
            val accountType = AccountType.Specific(accountKey)
            val user = createUser(MicroBlogKey(id = "user-long-cache", host = "test.com"), "User")
            var previousKey: MicroBlogKey? = null
            val posts =
                (0 until 6_000).map { index ->
                    val statusKey = MicroBlogKey(id = "cached-long-$index", host = "test.com")
                    createPost(
                        accountKey = accountKey,
                        user = user,
                        statusKey = statusKey,
                        text = "Cached long $index",
                        references =
                            previousKey
                                ?.let { key ->
                                    listOf(
                                        UiTimelineV2.Post.Reference(
                                            statusKey = key,
                                            type = ReferenceType.Reply,
                                        ),
                                    )
                                }.orEmpty(),
                    ).also {
                        previousKey = it.statusKey
                    }
                }
            val cached =
                TimelinePagingMapper.toDb(
                    timelinePostItem(
                        post = posts.last(),
                        inlineParents = posts.dropLast(1),
                    ),
                    pagingKey = "home",
                )

            val resolved =
                assertIs<UiTimelineV2.TimelinePostItem>(
                    TimelinePagingMapper.toUi(
                        item = cached,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(),
                    ),
                )

            assertEquals(posts.last().statusKey, resolved.statusKey)
            assertEquals(posts.dropLast(1).map { it.statusKey }, resolved.presentation.inlineParents.map { it.statusKey })
        }

    @Test
    fun cachedTimelineMapperRestoresReplyParentsByReferenceOrder() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-parent-order-cache", host = "test.com")
            val accountType = AccountType.Specific(accountKey)
            val user = createUser(MicroBlogKey(id = "user-parent-order-cache", host = "test.com"), "User")
            val parents =
                (0 until 5).map { index ->
                    createPost(
                        accountKey = accountKey,
                        user = user,
                        statusKey = MicroBlogKey(id = "parent-$index", host = "test.com"),
                        text = "Parent $index",
                    )
                }
            val root =
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = user,
                            statusKey = MicroBlogKey(id = "leaf", host = "test.com"),
                            text = "Leaf",
                        ),
                    inlineParents = parents,
                )
            val cached =
                TimelinePagingMapper.toDb(root, pagingKey = "home")

            val resolved =
                assertIs<UiTimelineV2.TimelinePostItem>(
                    TimelinePagingMapper.toUi(
                        item = cached,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(),
                    ),
                )

            assertEquals(
                listOf("parent-0", "parent-1", "parent-2", "parent-3", "parent-4"),
                resolved.presentation.inlineParents.map { it.statusKey.id },
            )
        }

    @Test
    fun cachedTimelineMapperKeepsStructuredParentsBeforeDirectReplyReference() =
        runTest {
            val accountKey = MicroBlogKey(id = "account-parent-order-direct-ref", host = "test.com")
            val user = createUser(MicroBlogKey(id = "user-parent-order-direct-ref", host = "test.com"), "User")
            val parents =
                (0 until 5).map { index ->
                    createPost(
                        accountKey = accountKey,
                        user = user,
                        statusKey = MicroBlogKey(id = "parent-$index", host = "test.com"),
                        text = "Parent $index",
                    )
                }
            val leaf =
                timelinePostItem(
                    post =
                        createPost(
                            accountKey = accountKey,
                            user = user,
                            statusKey = MicroBlogKey(id = "leaf", host = "test.com"),
                            text = "Leaf",
                            references =
                                listOf(
                                    UiTimelineV2.Post.Reference(
                                        statusKey = parents.last().statusKey,
                                        type = ReferenceType.Reply,
                                    ),
                                ),
                        ),
                    inlineParents = parents,
                )
            val saved = TimelinePagingMapper.toDb(leaf, pagingKey = "home")
            val savedRoot = saved.status.status.data.content as UiTimelineV2.Post

            assertEquals(
                listOf("parent-4"),
                savedRoot.references
                    .filter { it.type == ReferenceType.Reply }
                    .map { it.statusKey.id },
            )

            val resolved =
                assertIs<UiTimelineV2.TimelinePostItem>(
                    TimelinePagingMapper.toUi(
                        item = saved,
                        pagingKey = "home",
                        translationDisplayOptions = translationDisplayOptions(),
                    ),
                )

            assertEquals(
                listOf("parent-0", "parent-1", "parent-2", "parent-3", "parent-4"),
                resolved.presentation.inlineParents.map { it.statusKey.id },
            )
        }

    private suspend fun assertInFlightTranslationKeepsNoopTranslateAction(translationStatus: TranslationStatus) {
        val id = translationStatus.name.lowercase()
        val accountKey = MicroBlogKey(id = "account-$id", host = "test.com")
        val postUser =
            createUser(MicroBlogKey(id = "post-user-$id", host = "test.com"), "Post User")
        val post =
            createPost(
                accountKey = accountKey,
                user = postUser,
                statusKey = MicroBlogKey(id = "post-status-$id", host = "test.com"),
                text = "$id source",
            ).copy(
                actions = persistentListOf(moreMenu()),
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
                sourceHash =
                    post
                        .translationPayload()!!
                        .sourceHash(googleTranslationProviderCacheKey),
                status = translationStatus,
                payload = null,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )

        val paging = db.pagingTimelineDao().getPagingSource("home")
        val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
        val refreshResult = pager.refresh()
        val page =
            assertPage(
                refreshResult,
            )
        val dbItem = assertNotNull(page.data.firstOrNull())

        val timelineUi =
            rootPostOf(
                TimelinePagingMapper.toUi(
                    item = dbItem,
                    pagingKey = "home",
                    translationDisplayOptions = translationDisplayOptions(),
                ),
            )

        assertEquals("$id source", timelineUi.content.original.raw)
        assertEquals(TranslationDisplayState.Translating, timelineUi.translationDisplayState)
        val moreAction = assertIs<ActionMenu.Group>(timelineUi.actions.first())
        val translateAction = assertIs<ActionMenu.Item>(moreAction.actions.first())
        val translateText = assertIs<ActionMenu.Item.Text.Localized>(translateAction.text)
        assertEquals(ActionMenu.Item.Text.Localized.Type.Translate, translateText.type)
        assertEquals(ClickEvent.Noop, translateAction.clickEvent)
        assertEquals(UiIcon.Translate, translateAction.icon)
    }

    private fun moreMenu(actions: List<ActionMenu> = emptyList()): ActionMenu.Group =
        ActionMenu.Group(
            displayItem =
                ActionMenu.Item(
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                    clickEvent = ClickEvent.Noop,
                ),
            actions = actions.toPersistentList(),
        )

    private fun rootPostOf(item: UiTimelineV2): UiTimelineV2.Post =
        when (item) {
            is UiTimelineV2.TimelinePostItem -> item.post
            is UiTimelineV2.Post -> item
            else -> error("Expected post timeline item, got ${item::class}")
        }

    private fun assertPage(
        result: PagingSource.LoadResult<Int, DbPagingTimelineWithStatus>,
    ): PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus> =
        when (result) {
            is PagingSource.LoadResult.Page -> result
            is PagingSource.LoadResult.Error -> throw result.throwable
            is PagingSource.LoadResult.Invalid -> error("Paging source returned Invalid")
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
            matrices =
                UiProfile.Matrices(
                    fansCount = 0,
                    followsCount = 0,
                    statusesCount = 0,
                    platformFansCount = "0",
                ),
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
        references: List<UiTimelineV2.Post.Reference> = emptyList(),
    ): UiTimelineV2.Post {
        val semanticReferences =
            (
                references +
                    parents
                        .lastOrNull()
                        ?.let {
                            UiTimelineV2.Post.Reference(
                                statusKey = it.statusKey,
                                type = ReferenceType.Reply,
                            )
                        }.let(::listOfNotNull) +
                    quote.map {
                        UiTimelineV2.Post.Reference(
                            statusKey = it.statusKey,
                            type = ReferenceType.Quote,
                        )
                    }
            ).distinctBy { it.type to it.statusKey }
        return UiTimelineV2.Post(
            platformType = dev.dimension.flare.model.PlatformType.Mastodon,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = user,
            content = UiTranslatableText(text.toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            references = semanticReferences.toPersistentList(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(accountKey),
        )
    }

    private fun timelinePostItem(
        post: UiTimelineV2.Post,
        message: UiTimelineV2.Message? = null,
        inlineParents: List<UiTimelineV2.Post> = emptyList(),
        quotes: List<UiTimelineV2.Post> = emptyList(),
        repost: UiTimelineV2.Post? = null,
    ): UiTimelineV2.TimelinePostItem {
        val semanticReferences =
            (
                post.references +
                    inlineParents
                        .lastOrNull()
                        ?.let {
                            UiTimelineV2.Post.Reference(
                                statusKey = it.statusKey,
                                type = ReferenceType.Reply,
                            )
                        }.let(::listOfNotNull) +
                    quotes.map {
                        UiTimelineV2.Post.Reference(
                            statusKey = it.statusKey,
                            type = ReferenceType.Quote,
                        )
                    } +
                    repost
                        ?.let {
                            UiTimelineV2.Post.Reference(
                                statusKey = it.statusKey,
                                type = ReferenceType.Retweet,
                            )
                        }.let(::listOfNotNull)
            ).distinctBy { it.type to it.statusKey }
        return UiTimelineV2.TimelinePostItem(
            post = post.copy(references = semanticReferences.toPersistentList()),
            presentation =
                UiTimelineV2.PostPresentation(
                    message = message,
                    inlineParents = inlineParents.toPersistentList(),
                    quotes = quotes.toPersistentList(),
                    repost = repost,
                ),
        )
    }
}
