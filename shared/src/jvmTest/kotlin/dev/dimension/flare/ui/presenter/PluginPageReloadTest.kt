package dev.dimension.flare.ui.presenter

import androidx.paging.PagingData
import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.DataSourceCapabilityProvider
import dev.dimension.flare.data.datasource.microblog.DataSourceCapabilitySet
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationCapability
import dev.dimension.flare.data.datasource.microblog.ProfileCapability
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.UnavailableMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.ArticleDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.data.repository.RepositoryAccountService
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.model.fallbackString
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.uiArticleContentOf
import dev.dimension.flare.ui.presenter.article.ArticlePresenter
import dev.dimension.flare.ui.presenter.dm.DMConversationPresenter
import dev.dimension.flare.ui.presenter.gallery.GalleryDetailPresenter
import dev.dimension.flare.ui.presenter.home.NotificationBadgePresenter
import dev.dimension.flare.ui.presenter.profile.ProfilePresenter
import dev.dimension.flare.ui.presenter.profile.ProfileWithUserNameAndHostPresenter
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PluginPageReloadTest {
    private val accountKey = MicroBlogKey("alice", "plugin.example")
    private val accountType = AccountType.Specific(accountKey)
    private val contentKey = MicroBlogKey("content", accountKey.host)

    @Test
    fun openArticleAndCommentsRecoverWhenTheCapabilityReturns() =
        withFixture {
            attach(Source("first"))
            val state = observe(ArticlePresenter(accountType, contentKey))
            state.await { it.article.takeSuccess()?.title == "first" && it.comments.firstTitle() == "first" }

            attach(object : MicroblogDataSource by UnavailableMicroblogDataSource {})
            state.await { it.article is UiState.Error && it.comments is PagingState.Error }

            attach(Source("restored"))
            state.await { it.article.takeSuccess()?.title == "restored" && it.comments.firstTitle() == "restored" }
        }

    @Test
    fun openGalleryRecoversAfterTheDevelopmentPlatformIsReattached() =
        withFixture {
            attach(Source("first"))
            val state = observe(GalleryDetailPresenter(accountType, contentKey))
            state.await { it.detail.takeSuccess()?.title == "first" }

            detach()
            state.await { it.detail is UiState.Error }

            attach(Source("restored"))
            state.await { it.detail.takeSuccess()?.title == "restored" && it.comments.firstTitle() == "restored" }
        }

    @Test
    fun openProfileAndTabsRecoverAfterTheDevelopmentPlatformIsReattached() =
        withFixture {
            attach(Source("first"))
            val state = observe(ProfilePresenter(accountType, userKey = null))
            state.await {
                it.userState
                    .takeSuccess()
                    ?.name
                    ?.raw == "first" &&
                    it.tabs
                        .takeSuccess()
                        ?.singleOrNull()
                        ?.name
                        ?.fallbackString == "first"
            }

            detach()
            state.await { it.userState is UiState.Error }

            attach(Source("restored"))
            val restored =
                state.await {
                    it.userState
                        .takeSuccess()
                        ?.name
                        ?.raw == "restored" &&
                        it.tabs
                            .takeSuccess()
                            ?.singleOrNull()
                            ?.name
                            ?.fallbackString == "restored"
                }
            assertEquals(accountKey, restored.myAccountKey.takeSuccess())
        }

    @Test
    fun openProfileByHandleRecoversWhenTheProfileCapabilityReturns() =
        withFixture {
            attach(Source("first"))
            val state = observe(ProfileWithUserNameAndHostPresenter(accountKey.id, accountKey.host, accountType))
            state.await {
                it.user
                    .takeSuccess()
                    ?.name
                    ?.raw == "first"
            }

            detach()
            state.await { it.user is UiState.Error }

            attach(Source("restored"))
            state.await {
                it.user
                    .takeSuccess()
                    ?.name
                    ?.raw == "restored"
            }
        }

    @Test
    fun notificationBadgeFetchesFromTheReloadedSource() =
        withFixture {
            val first = Source("first", badgeCount = 1)
            attach(first)
            val state = observe(NotificationBadgePresenter(accountType, autoRefresh = false))
            state.await { it.count.takeSuccess() == 1 }

            detach()
            state.await { it.count.takeSuccess() == 0 }

            val replacement = Source("replacement", badgeCount = 2)
            attach(replacement)
            state.await { it.count.takeSuccess() == 2 }
            assertEquals(1, replacement.badgeFetches.value)
        }

    @Test
    fun legacyConversationPollingMovesToTheReloadedSource() =
        withFixture {
            val first = Source("first")
            attach(requireNotNull(first.capabilitySet.directMessage))
            val state = observe(DMConversationPresenter(accountType, contentKey))
            state.await {
                it.users
                    .takeSuccess()
                    ?.singleOrNull()
                    ?.name
                    ?.raw == "first"
            }

            detach()
            state.await { it.users is UiState.Error }

            val replacement = Source("replacement")
            attach(requireNotNull(replacement.capabilitySet.directMessage))
            state.await {
                it.users
                    .takeSuccess()
                    ?.singleOrNull()
                    ?.name
                    ?.raw == "replacement"
            }
            // Exercise the real ten-second polling effect, not just its rendered source.
            withTimeout(12_000) { replacement.polls.first { it > 0 } }
            assertEquals(0, first.polls.value)
        }

    private fun withFixture(block: suspend Fixture.() -> Unit) =
        runBlocking {
            val main = Executors.newSingleThreadExecutor { Thread(it, "plugin-page-reload") }.asCoroutineDispatcher()
            Dispatchers.setMain(main)
            val fixture = Fixture(CoroutineScope(main + SupervisorJob()))
            try {
                withContext(main) {
                    withTimeout(15_000) {
                        fixture.repository.addAccount(UiAccount(accountKey, PLATFORM), "credential")
                        fixture.block()
                    }
                }
            } finally {
                fixture.close()
                Dispatchers.resetMain()
                main.close()
            }
        }

    private inner class Fixture(
        val scope: CoroutineScope,
    ) {
        val root = createTestRootPath()
        val storage = OkioFileStorage(createTestFileSystem(), root)
        val app = Room.memoryDatabaseBuilder<AppDatabase>().setDriver(createDatabaseDriver()).build()
        val cache = Room.memoryDatabaseBuilder<CacheDatabase>().setDriver(createDatabaseDriver()).build()
        val appData = AppDataStore(storage)
        val registry = testPlatformRegistry()
        val repository = AccountRepository(app, scope, appData, cache, ProxyRepository(storage), registry)

        init {
            startKoin {
                modules(
                    module {
                        testSingle { cache }
                        testSingle { appData }
                        testSingle { repository }
                        testSingle<AccountService> { RepositoryAccountService(repository) }
                        testSingle<PreTranslationService> { NoopPreTranslationService }
                    },
                )
            }
        }

        fun attach(source: MicroblogDataSource) {
            registry.replaceExternal(
                PLATFORM,
                object : PlatformSpec by registry.defaultGuest {
                    override val platformId = PLATFORM
                    override val isDefaultGuest = false

                    override fun createDataSource(context: PlatformDataSourceContext) = source
                },
            )
        }

        fun detach() = registry.replaceExternal(PLATFORM, null)

        fun <T : Any> observe(presenter: PresenterBase<T>): MutableStateFlow<T?> {
            val state = MutableStateFlow<T?>(null)
            scope.launch { moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { state.value = it } }
            return state
        }

        suspend fun close() {
            scope.coroutineContext.job.cancelAndJoin()
            app.close()
            cache.close()
            stopKoin()
            deleteTestRootPath(root)
        }
    }

    private inner class Source(
        private val marker: String,
        private val badgeCount: Int = 1,
    ) : MicroblogDataSource by UnavailableMicroblogDataSource,
        DataSourceCapabilityProvider {
        val polls = MutableStateFlow(0)
        val badgeFetches = MutableStateFlow(0)
        override val authenticatedAccountKey = accountKey
        override val authenticatedProfile = createSampleUser().copy(key = accountKey, nameInternal = marker.toUiPlainText())
        private val comments =
            object : RemoteLoader<UiTimelineV2> {
                override suspend fun load(
                    pageSize: Int,
                    request: PagingRequest,
                ): PagingResult<UiTimelineV2> =
                    PagingResult(
                        data =
                            listOf(
                                UiTimelineV2.Feed(
                                    title = marker,
                                    description = marker,
                                    url = "https://${accountKey.host}/$marker",
                                    createdAt = Instant.fromEpochMilliseconds(1).toUi(),
                                    source = UiTimelineV2.Feed.Source(marker, null),
                                    accountType = accountType,
                                ),
                            ),
                        endOfPaginationReached = true,
                    )
            }
        override val capabilitySet =
            DataSourceCapabilitySet(
                notification =
                    NotificationCapability(
                        null,
                        object : NotificationDataSource {
                            override val notificationHandler =
                                NotificationHandler(
                                    accountKey,
                                    object : NotificationLoader {
                                        override suspend fun notificationBadgeCount(): Int {
                                            badgeFetches.value += 1
                                            return badgeCount
                                        }
                                    },
                                )
                        },
                    ),
                directMessage =
                    object : DirectMessageDataSource, MicroblogDataSource by this {
                        override val accountKey = this@PluginPageReloadTest.accountKey
                        override val directMessageHandler get() = error("Unused")

                        override fun directMessageConversation(
                            roomKey: MicroBlogKey,
                            scope: CoroutineScope,
                        ) = flowOf(PagingData.empty<UiDMItem>())

                        override fun getDirectMessageConversationInfo(roomKey: MicroBlogKey) =
                            Cacheable(
                                fetchSource = {},
                                cacheSource = { flowOf(UiDMRoom(roomKey, persistentListOf(authenticatedProfile), null, 0)) },
                            )

                        override suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey) {
                            polls.value += 1
                        }

                        override suspend fun canSendDirectMessage(userKey: MicroBlogKey) = false
                    },
                article =
                    object : ArticleDataSource {
                        override suspend fun article(articleKey: MicroBlogKey) =
                            UiArticle(
                                key = articleKey.id,
                                title = marker,
                                content = uiArticleContentOf(emptyList()),
                            )

                        override fun articleComments(articleKey: MicroBlogKey) = comments
                    },
                gallery =
                    object : GalleryDataSource {
                        override fun galleryDetail(statusKey: MicroBlogKey) =
                            Cacheable(
                                fetchSource = {},
                                cacheSource = {
                                    flowOf(
                                        GalleryDetail(
                                            orientation = GalleryOrientation.Horizontal,
                                            statusKey = statusKey,
                                            accountType = accountType,
                                            url = "https://${accountKey.host}/$marker",
                                            images = persistentListOf(),
                                            title = marker,
                                            author = authenticatedProfile,
                                            createdAt = Instant.fromEpochMilliseconds(1).toUi(),
                                            content = null,
                                            isBookmarked = false,
                                            bookmarkAction = ClickEvent.Noop,
                                            matrix = persistentListOf(),
                                        ),
                                    )
                                },
                            )

                        override fun galleryComments(statusKey: MicroBlogKey) = comments

                        override fun galleryRecommendations(statusKey: MicroBlogKey) = comments

                        override suspend fun ugoiraMetadata(statusKey: MicroBlogKey): Nothing = error("unused")
                    },
                profile =
                    ProfileCapability(
                        this,
                        object : UserDataSource {
                            override val userHandler =
                                UserHandler(
                                    accountKey.host,
                                    object : UserLoader {
                                        override suspend fun userByHandleAndHost(uiHandle: UiHandle) = authenticatedProfile

                                        override suspend fun userById(id: String) = authenticatedProfile
                                    },
                                )
                        },
                    ),
            )

        override suspend fun profileTabs(userKey: MicroBlogKey) =
            persistentListOf(
                ProfileTab(name = UiText.Raw(marker), loader = comments, id = "posts"),
            )
    }

    private suspend fun <T : Any> MutableStateFlow<T?>.await(predicate: (T) -> Boolean): T =
        withTimeout(5_000) { filterNotNull().first(predicate) }

    private fun PagingState<UiTimelineV2>.firstTitle() = ((this as? PagingState.Success)?.peek(0) as? UiTimelineV2.Feed)?.title

    private companion object {
        const val PLATFORM = "PluginPageReload"
    }
}
