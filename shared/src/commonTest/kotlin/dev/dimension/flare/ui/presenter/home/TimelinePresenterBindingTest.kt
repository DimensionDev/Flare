package dev.dimension.flare.ui.presenter.home

import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.tab.GroupSource
import dev.dimension.flare.data.model.tab.SYSTEM_HOME_MIXED_TIMELINE_ID
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.data.model.tab.TimelinePresentation
import dev.dimension.flare.data.model.tab.TimelinePresenterFactory
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import dev.dimension.flare.data.model.tab.TimelineSourceRef
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.UiGroupTimelineTabItem
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.data.model.tab.remoteLoaderFactory
import dev.dimension.flare.data.model.tab.toTimelineSlotOrNull
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.testPlatformRuntimeData
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.unavailableAccountService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelinePresenterBindingTest : RobolectricTest() {
    private lateinit var cacheDatabase: CacheDatabase
    private lateinit var root: Path
    private lateinit var timelineResolver: TimelineResolver
    private lateinit var appDataStore: AppDataStore
    private lateinit var settingsRepository: SettingsRepository

    @BeforeTest
    fun setup() {
        cacheDatabase =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(
                    createDatabaseDriver(),
                ).setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        root = createTestRootPath()
        val fileStorage = OkioFileStorage(createTestFileSystem(), root)
        timelineResolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService())
        appDataStore = AppDataStore(fileStorage)
        settingsRepository =
            SettingsRepository(
                fileStorage = fileStorage,
                cacheDatabase = cacheDatabase,
                appDataStore = appDataStore,
                timelineResolver = timelineResolver,
            )
    }

    @AfterTest
    fun tearDown() {
        cacheDatabase.close()
        deleteTestRootPath(root)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deletingAHomeTabInvalidatesItsPresenterAndBookmark() =
        runTest {
            val slot = homeSlot(id = "home:first", filterConfig = TimelineFilterConfig())
            settingsRepository.updateTabSettingsV2 { TabSettingsV2(homeSlots = listOf(slot)) }
            val resolver =
                TimelineResolver(
                    testPlatformRuntimeData(),
                    object : dev.dimension.flare.data.repository.AccountService by unavailableAccountService() {
                        override fun accountServiceFlow(
                            accountType: AccountType,
                        ): kotlinx.coroutines.flow.Flow<dev.dimension.flare.data.datasource.microblog.MicroblogDataSource> =
                            kotlinx.coroutines.flow.emptyFlow()
                    },
                )
            val presenter = TimelinePresenterFactory(resolver, settingsRepository).create(resolver.toTabItem(slot), true)
            val active = mutableListOf<Boolean>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { presenter.sourceActive.collect { active += it } }
            appDataStore.homeTimelinePreferences.updateData {
                it.copy(
                    positions =
                        mapOf(
                            "home:first" to
                                dev.dimension.flare.data.datastore.model.HomeTimelinePosition(
                                    "source",
                                    dev.dimension.flare.ui.model
                                        .TimelineReadingPosition(1, 0.0),
                                    1,
                                ),
                        ),
                )
            }
            settingsRepository.updateTabSettingsV2 { TabSettingsV2() }
            advanceUntilIdle()
            assertEquals(listOf(true, false), active)
            assertFalse(presenter.sourceActive.first(), "A queued save must recheck the deleted tab, even in a new collection")
            assertEquals(
                null,
                appDataStore.homeTimelinePreferences.data
                    .first()
                    .positions["home:first"],
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun filterConfigObservationUsesFixedTimelineTabItemId() =
        runTest {
            val firstSlot =
                homeSlot(
                    id = "home:first",
                    filterConfig =
                        TimelineFilterConfig(
                            excludedKinds = listOf(TimelinePostKind.Reply),
                        ),
                )
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(firstSlot))
            }

            val observed = mutableListOf<List<TimelinePostKind>>()
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    observeTimelineFilterConfig(
                        settingsRepository = settingsRepository,
                        timelineTabItemId = firstSlot.id,
                    ).map { it.excludedKinds }
                        .take(2)
                        .collect(observed::add)
                }
            advanceUntilIdle()
            settingsRepository.updateTabSettingsV2 {
                copy(
                    homeSlots =
                        listOf(
                            firstSlot.copy(
                                presentation =
                                    TimelinePresentation(
                                        filterConfig =
                                            TimelineFilterConfig(
                                                excludedKinds = listOf(TimelinePostKind.Repost),
                                            ),
                                    ),
                            ),
                        ),
                )
            }
            advanceUntilIdle()
            job.join()

            assertEquals(
                listOf(
                    listOf(TimelinePostKind.Reply),
                    listOf(TimelinePostKind.Repost),
                ),
                observed,
            )
        }

    @Test
    fun launchRefreshSettingOnlyAppliesToHomeTimeline() =
        runTest {
            var settingReads = 0
            val disabledSetting =
                suspend {
                    settingReads += 1
                    false
                }

            assertTrue(
                shouldRefreshTimelineOnInitialize(
                    isHomeTimeline = false,
                    refreshHomeTimelineOnLaunch = disabledSetting,
                ),
            )
            assertEquals(0, settingReads)
            assertFalse(
                shouldRefreshTimelineOnInitialize(
                    isHomeTimeline = true,
                    refreshHomeTimelineOnLaunch = disabledSetting,
                ),
            )
            assertEquals(1, settingReads)
        }

    @Test
    fun systemHomeMixedTimelineKeepsStableIdButChangesLoaderKeyWhenChildIsDisabled() =
        runTest {
            val firstSlot = homeSlot(accountId = "first")
            val secondSlot = homeSlot(accountId = "second")
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(
                    homeSlots =
                        listOf(
                            systemHomeMixedSlot(firstSlot, secondSlot),
                            firstSlot,
                            secondSlot,
                        ),
                )
            }
            val initialMixedTab = systemHomeMixedTab()

            val disabledSecondSlot = secondSlot.copy(presentation = TimelinePresentation(enabled = false))
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(
                    homeSlots =
                        listOf(
                            systemHomeMixedSlot(firstSlot, disabledSecondSlot),
                            firstSlot,
                            disabledSecondSlot,
                        ),
                )
            }
            val updatedMixedTab = systemHomeMixedTab()

            assertEquals(initialMixedTab.id, updatedMixedTab.id)
            assertEquals(
                listOf(true, true),
                initialMixedTab.children.map { it.enabled },
            )
            assertEquals(
                listOf(true, false),
                updatedMixedTab.children.map { it.enabled },
            )
            assertNotEquals(
                initialMixedTab.loaderKey,
                updatedMixedTab.loaderKey,
            )
            assertEquals(setOf(MicroBlogKey("first", "example.com")), timelineResolver.accountKeys(updatedMixedTab))
        }

    @Test
    fun timelinePresenterFactoryUsesDynamicPresenterForSystemHomeMixedTimeline() =
        runTest {
            val firstSlot = homeSlot(accountId = "first")
            val secondSlot = homeSlot(accountId = "second")
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(
                    homeSlots =
                        listOf(
                            systemHomeMixedSlot(firstSlot, secondSlot),
                            firstSlot,
                            secondSlot,
                        ),
                )
            }

            val presenter =
                TimelinePresenterFactory(timelineResolver, settingsRepository)
                    .create(systemHomeMixedTab())

            assertIs<SystemHomeMixedTimelinePresenter>(presenter)
        }

    @Test
    fun savingTabsPreservesExcludedSourcesAndGroupsInsideManualGroup() =
        runTest {
            val visible = homeSlot(accountId = "visible")
            val excluded = homeSlot(accountId = "excluded").copy(presentation = TimelinePresentation(enabled = false))
            val excludedGroup =
                TimelineSlot(
                    id = "excluded-group",
                    content = TimelineSlotContent.Group(children = listOf(homeSlot(accountId = "nested"))),
                    presentation = TimelinePresentation(enabled = false),
                )
            val group =
                TimelineSlot(
                    id = "custom",
                    content = TimelineSlotContent.Group(children = listOf(visible, excluded, excludedGroup)),
                )
            settingsRepository.updateTabSettingsV2 { TabSettingsV2(homeSlots = listOf(group)) }

            val tabs = settingsRepository.homeTimelineTabs.first()
            val item = assertIs<UiGroupTimelineTabItem>(tabs.single())
            val visibleOnly = group.copy(content = TimelineSlotContent.Group(children = listOf(visible)))
            assertEquals(timelineResolver.toTabItem(visibleOnly).contentSourceKey, item.contentSourceKey)
            assertEquals(setOf(MicroBlogKey("visible", "example.com")), timelineResolver.accountKeys(item))

            settingsRepository.replaceHomeTimelineTabs(tabs)

            assertEquals(listOf(group), settingsRepository.tabSettingsV2.first().homeSlots)
        }

    @Test
    fun unavailableTimelineSpecKeepsPersistedTabWithoutCrashing() =
        runTest {
            val original = homeSlot(accountId = "plugin-account")
            val source = assertIs<TimelineSlotContent.Source>(original.content).source
            val unavailable =
                original.copy(
                    content = TimelineSlotContent.Source(source.copy(specId = "plugin.uninstalled.home")),
                )
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(unavailable))
            }

            val tab = settingsRepository.homeTimelineTabs.first().single()

            assertNull(timelineResolver.resolveAccountKey(unavailable))
            assertTrue(
                timelineResolver
                    .resolveLoader(tab)
                    .first()
                    .load(20, PagingRequest.Refresh)
                    .data
                    .isEmpty(),
            )
            assertEquals(unavailable.id, tab.id)
        }

    @Test
    fun malformedPersistedTimelineDataBecomesUnavailableInsteadOfCrashing() =
        runTest {
            val original = homeSlot(accountId = "corrupt")
            val source = assertIs<TimelineSlotContent.Source>(original.content).source
            val malformed =
                original.copy(
                    content =
                        TimelineSlotContent.Source(
                            source.copy(data = "not-protobuf", ownerAccountKey = null),
                        ),
                )
            val tab = timelineResolver.toTabItem(malformed)

            assertNull(timelineResolver.resolveAccountKey(malformed))
            assertTrue(
                timelineResolver
                    .resolveLoader(tab)
                    .first()
                    .load(20, PagingRequest.Refresh)
                    .data
                    .isEmpty(),
            )
        }

    @Test
    fun ambiguousTimelineSpecIdIsUnavailableButRepeatedSameSpecIsSupported() =
        runTest {
            val accountKey = MicroBlogKey("duplicate", "example.com")
            val spec =
                TimelineSpec(
                    id = "duplicate.timeline",
                    title = UiText.Raw("Duplicate"),
                    icon = UiIcon.World.asType(),
                    serializer = TimelineSpec.AccountBasedData.serializer(),
                    targetId = { it.accountKey.toString() },
                    loaderFactory = remoteLoaderFactory { notSupported() },
                )
            val singleResolver =
                TimelineResolver(
                    PlatformRuntimeData(emptyList(), listOf(spec)),
                    unavailableAccountService(),
                )
            val source =
                assertIs<TimelineSlotContent.Source>(
                    singleResolver
                        .toSlot(spec.candidate(TimelineSpec.AccountBasedData(accountKey)))
                        .content,
                ).source.copy(ownerAccountKey = null)
            val slot = source.toTestSlot()

            val ambiguousResolver =
                TimelineResolver(
                    PlatformRuntimeData(emptyList(), listOf(spec, spec.copy(title = UiText.Raw("Other")))),
                    unavailableAccountService(),
                )
            assertNull(ambiguousResolver.resolveAccountKey(slot))

            val repeatedResolver =
                TimelineResolver(
                    PlatformRuntimeData(emptyList(), listOf(spec, spec)),
                    unavailableAccountService(),
                )
            assertEquals(accountKey, repeatedResolver.resolveAccountKey(slot))
        }

    private suspend fun systemHomeMixedTab(): UiGroupTimelineTabItem {
        val tab =
            settingsRepository.homeTimelineTabs
                .map { tabs -> tabs.first { it.isSystemHomeMixedTimeline } }
                .first()
        return assertIs<UiGroupTimelineTabItem>(tab)
    }

    private fun systemHomeMixedSlot(vararg children: TimelineSlot): TimelineSlot =
        TimelineSlot(
            id = SYSTEM_HOME_MIXED_TIMELINE_ID,
            content =
                TimelineSlotContent.Group(
                    children = children.toList(),
                    source = GroupSource.SystemHome,
                    mergePolicy = TimelineMergePolicy.TimePerPage,
                ),
        )

    private fun homeSlot(accountId: String): TimelineSlot =
        requireNotNull(
            HomeTimelineTabItem(AccountType.Specific(MicroBlogKey(id = accountId, host = "example.com")))
                .toTimelineSlotOrNull(),
        )

    private fun homeSlot(
        id: String,
        filterConfig: TimelineFilterConfig,
    ) = requireNotNull(
        HomeTimelineTabItem(AccountType.Specific(MicroBlogKey(id = "home", host = "example.com")))
            .toTimelineSlotOrNull(),
    ).copy(
        id = id,
        presentation = TimelinePresentation(filterConfig = filterConfig),
    )

    private fun TimelineSourceRef.toTestSlot() =
        TimelineSlot(
            id = id,
            content = TimelineSlotContent.Source(this),
        )
}
