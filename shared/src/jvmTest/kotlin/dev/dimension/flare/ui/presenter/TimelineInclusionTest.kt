package dev.dimension.flare.ui.presenter

import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.HomeTimelineRefreshSessions
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.GroupSource
import dev.dimension.flare.data.model.tab.SYSTEM_HOME_MIXED_TIMELINE_ID
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelinePresentation
import dev.dimension.flare.data.model.tab.TimelinePresenterFactory
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.remoteLoaderFactory
import dev.dimension.flare.data.model.tab.toSlot
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.MxgaRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.TimelineState
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.unavailableAccountService
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineInclusionTest {
    @Test fun excludedSourceLoadsItsOwnPage() = checkInclusion(group = false, enabledAtStart = false)

    @Test fun excludedGroupLoadsItsOwnPage() = checkInclusion(group = true, enabledAtStart = false)

    @Test fun excludingAnOpenSourceKeepsItsPageLoaded() = checkInclusion(group = false, enabledAtStart = true)

    @Test fun excludingAnOpenGroupKeepsItsPageLoaded() = checkInclusion(group = true, enabledAtStart = true)

    @Test fun excludedSourceLoadsFromNonHomeEntry() = checkInclusion(group = false, enabledAtStart = false, isHomeTimeline = false)

    @Test fun excludedGroupLoadsFromNonHomeEntry() = checkInclusion(group = true, enabledAtStart = false, isHomeTimeline = false)

    private fun checkInclusion(
        group: Boolean,
        enabledAtStart: Boolean,
        isHomeTimeline: Boolean = true,
    ) = runBlocking {
        val main = Executors.newSingleThreadExecutor { Thread(it, "timeline-ui") }.asCoroutineDispatcher()
        Dispatchers.setMain(main)
        val root = createTestRootPath()
        val storage = OkioFileStorage(createTestFileSystem(), root)
        val cache =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        val app =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val targetRequests = AtomicInteger()
            val version = AtomicInteger(1)
            val spec =
                TimelineSpec(
                    id = "inclusion-test",
                    title = UiStrings.Home,
                    icon = UiIcon.Home.asType(),
                    serializer = TimelineSpec.AccountBasedData.serializer(),
                    targetId = { it.accountKey.id },
                    loaderFactory =
                        remoteLoaderFactory { data ->
                            object : CacheableRemoteLoader<UiTimelineV2> {
                                override val pagingKey = "inclusion:${data.accountKey.id}"

                                override suspend fun load(
                                    pageSize: Int,
                                    request: PagingRequest,
                                ): PagingResult<UiTimelineV2> {
                                    if (data.accountKey.id == "target") targetRequests.incrementAndGet()
                                    return PagingResult(listOf(post("${data.accountKey.id}-${version.get()}")))
                                }
                            }
                        },
                )
            val resolver =
                TimelineResolver(
                    PlatformRuntimeData(platformSpecs = emptyList(), extraTimelineSpecs = listOf(spec)),
                    unavailableAccountService(),
                )
            val appData = AppDataStore(storage)
            appData.appSettingsStore.updateData {
                it.copy(rememberHomeTimelinePosition = false, refreshHomeTimelineOnLaunch = true, mxgaEnabled = false)
            }
            val settings = SettingsRepository(storage, appData, resolver, cache)
            val factory = TimelinePresenterFactory(resolver, settings)
            startKoin {
                modules(
                    module {
                        testSingle { cache }
                        testSingle { app }
                        testSingle { appData }
                        testSingle { settings }
                        testSingle { resolver }
                        testSingle { factory }
                        testSingle { HomeTimelineRefreshSessions() }
                        testSingle<CoroutineScope> { scope }
                        testSingle<PreTranslationService> { NoopPreTranslationService }
                        testSingle { LocalFilterRepository(app, scope) }
                        testSingle { MxgaRepository(storage, settings, scope) }
                        testSingle<PlatformRegistry> { testPlatformRegistry() }
                        testSingle<PlatformFormatter> { TestFormatter() }
                    },
                )
            }
            val child = spec.source(TimelineSpec.AccountBasedData(MicroBlogKey("target", "example.invalid"))).toSlot()
            val included = spec.source(TimelineSpec.AccountBasedData(MicroBlogKey("included", "example.invalid"))).toSlot()
            val target =
                (
                    if (group) {
                        TimelineSlot(id = "custom-group", content = TimelineSlotContent.Group(children = listOf(child)))
                    } else {
                        child
                    }
                ).copy(presentation = TimelinePresentation(enabled = enabledAtStart))

            suspend fun storeTarget(slot: TimelineSlot) {
                settings.updateTabSettingsV2 {
                    TabSettingsV2(
                        homeSlots =
                            listOf(
                                TimelineSlot(
                                    id = SYSTEM_HOME_MIXED_TIMELINE_ID,
                                    content = TimelineSlotContent.Group(children = listOf(slot, included), source = GroupSource.SystemHome),
                                ),
                                slot,
                                included,
                            ),
                    )
                }
            }
            storeTarget(target)
            val presenter = factory.create(checkNotNull(settings.homeTimelineTab(target.id).first()), isHomeTimeline = isHomeTimeline)
            val models = MutableStateFlow<TimelineState?>(null)
            scope.launch(Dispatchers.Main) {
                moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { models.value = it }
            }

            suspend fun awaitPost(id: String): TimelineState =
                withTimeout(5_000) {
                    models.filterNotNull().first { model ->
                        val items = model.listState as? PagingState.Success
                        items != null && (0 until items.itemCount).any { items.peek(it)?.statusKey?.id == id }
                    }
                }

            val initial = awaitPost("target-1")
            assertEquals(1, (initial.listState as PagingState.Success).itemCount)
            assertEquals(1, targetRequests.get())
            storeTarget(target.copy(presentation = TimelinePresentation(enabled = false)))
            assertTrue(presenter.sourceActive.first(), "Excluding a timeline from mixed timelines must keep its own page active")

            version.set(2)
            withContext(Dispatchers.Main) { checkNotNull(models.value).refresh() }
            val refreshed = awaitPost("target-2")
            assertEquals(1, (refreshed.listState as PagingState.Success).itemCount)
            assertEquals(2, targetRequests.get())

            val mixed =
                factory
                    .create(checkNotNull(settings.homeTimelineTab(SYSTEM_HOME_MIXED_TIMELINE_ID).first()))
                    .loader
                    .first() as CacheableRemoteLoader<UiTimelineV2>
            val mixedResult = mixed.load(20, PagingRequest.Refresh)
            assertEquals(listOf("included-2"), mixedResult.data.map { it.statusKey.id })
            assertEquals(2, targetRequests.get(), "The mixed timeline must not request the excluded source or group")
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            stopKoin()
            cache.close()
            app.close()
            Dispatchers.resetMain()
            main.close()
            deleteTestRootPath(root)
        }
    }

    private fun post(id: String) =
        UiTimelineV2.Post(
            platformId = "xQt",
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = UiTranslatableText(id.toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey(id, "example.invalid"),
            card = null,
            createdAt = Instant.fromEpochMilliseconds(1).toUi(),
            references = persistentListOf(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Guest,
        )
}
