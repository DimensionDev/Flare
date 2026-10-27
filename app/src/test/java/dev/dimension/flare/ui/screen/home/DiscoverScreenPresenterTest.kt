package dev.dimension.flare.ui.screen.home

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.di.AndroidKoinApplication
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.core.definition.Definition
import org.koin.dsl.module
import org.koin.plugin.module.dsl.startKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue
import org.koin.core.module.Module as KoinModule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class DiscoverScreenPresenterTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val requests = ConcurrentLinkedQueue<Pair<MicroBlogKey, String>>()
    private val first = MicroBlogKey("alice", "example.social")
    private val second = MicroBlogKey("bob", "example.social")
    private lateinit var app: KoinApplication

    @Before
    fun setUp() {
        val platform =
            object : PlatformSpec by MastodonPlatformSpec {
                override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
                    SearchSource(context.accountKey, requests)
            }
        app =
            startKoin<AndroidKoinApplication> {
                androidContext(RuntimeEnvironment.getApplication())
                modules(
                    module {
                        testSingle { scope }
                        testSingle { PlatformRegistry(PlatformRuntimeData(listOf(platform), emptyList())) }
                    },
                )
            }
        val accounts = app.koin.get<AccountService>()
        runBlocking {
            listOf(first, second).forEach { key ->
                accounts.addAccount(UiAccount(key, platform.platformId), "unused").join()
            }
        }
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        stopKoin()
    }

    @Test
    fun accountSwitchingReplaysOnlySubmittedSearchesAndClearingForgetsThem() {
        lateinit var state: DiscoverScreenState
        composeRule.setContent {
            val model = discoverPresenter()
            SideEffect { state = model }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { state.isAccountReady }
        composeRule.runOnIdle { state.commitSearch("cats") }
        composeRule.waitUntil(timeoutMillis = 10_000) { requests.size >= 2 }
        repeat(2) { assertEquals(first to "cats", requests.poll()) }

        composeRule.runOnIdle {
            state.queryTextState.edit { replace(0, length, "dogs") }
            state.setAccountKey(second)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { state.selectedAccountKey == second && requests.size >= 2 }
        repeat(2) { assertEquals(second to "cats", requests.poll()) }

        composeRule.runOnIdle { state.clearSearch() }
        composeRule.waitUntil { !state.isInSearchMode }
        composeRule.runOnIdle {
            state.queryTextState.edit { append("unsubmitted") }
            state.setAccountKey(first)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { state.selectedAccountKey == first }
        composeRule.runOnIdle {
            assertFalse(state.searchState.searching)
            assertFalse(requests.isNotEmpty())
        }
    }

    @Test
    fun accountRestorationDoesNotSubmitAnUnconfirmedDraft() {
        lateinit var state: DiscoverScreenState
        var draftEntered = false
        composeRule.setContent {
            val model = discoverPresenter()
            SideEffect {
                state = model
                if (!draftEntered) {
                    assertFalse(model.isAccountReady)
                    model.queryTextState.edit { append("unsubmitted") }
                    draftEntered = true
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { state.isAccountReady }
        composeRule.runOnIdle {
            assertFalse(state.searchState.searching)
            assertFalse(requests.isNotEmpty())
        }
    }

    @Test
    fun searchSubmittedDuringRestorationUsesTheSubmittedQueryAfterInitialization() {
        var submitted = false
        composeRule.setContent {
            val model = discoverPresenter()
            SideEffect {
                if (!submitted) {
                    assertFalse(model.isAccountReady)
                    model.commitSearch("cats")
                    model.queryTextState.edit { replace(0, length, "dogs") }
                    submitted = true
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { requests.size >= 2 }
        repeat(2) { assertEquals(first to "cats", requests.poll()) }
    }

    private class SearchSource(
        override val accountKey: MicroBlogKey,
        private val requests: ConcurrentLinkedQueue<Pair<MicroBlogKey, String>>,
    ) : AuthenticatedMicroblogDataSource,
        UserDataSource {
        override val userHandler =
            UserHandler(
                accountKey.host,
                object : UserLoader {
                    override suspend fun userById(id: String): UiProfile = error("Profile unavailable")

                    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile = error("Profile unavailable")
                },
            )

        override fun searchStatus(query: String) = notSupported<UiTimelineV2>().also { requests.add(accountKey to query) }

        override fun searchUser(query: String) = notSupported<UiProfile>().also { requests.add(accountKey to query) }

        override fun homeTimeline() = notSupported<UiTimelineV2>()

        override fun userTimeline(
            userKey: MicroBlogKey,
            mediaOnly: Boolean,
        ) = notSupported<UiTimelineV2>()

        override fun context(statusKey: MicroBlogKey) = notSupported<UiTimelineV2>()

        override fun discoverUsers() = notSupported<UiProfile>()

        override fun discoverStatuses() = notSupported<UiTimelineV2>()

        override fun discoverHashtags() = notSupported<UiHashtag>()

        override fun following(userKey: MicroBlogKey) = notSupported<UiProfile>()

        override fun fans(userKey: MicroBlogKey) = notSupported<UiProfile>()

        override suspend fun profileTabs(userKey: MicroBlogKey) = persistentListOf<ProfileTab>()
    }
}

private inline fun <reified T> KoinModule.testSingle(noinline definition: Definition<T>) = single(definition = definition)
