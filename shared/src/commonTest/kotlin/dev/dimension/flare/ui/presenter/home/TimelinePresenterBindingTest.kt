package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.data.model.tab.TimelinePresentation
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.toTimelineSlotOrNull
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.testPlatformRuntimeData
import dev.dimension.flare.unavailableAccountService
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

class TimelinePresenterBindingTest {
    private lateinit var root: Path
    private lateinit var settingsRepository: SettingsRepository

    @BeforeTest
    fun setup() {
        root = createTestRootPath()
        val fileStorage = OkioFileStorage(createTestFileSystem(), root)
        settingsRepository =
            SettingsRepository(
                fileStorage = fileStorage,
                appDataStore = AppDataStore(fileStorage),
                timelineResolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService()),
            )
    }

    @AfterTest
    fun tearDown() {
        deleteTestRootPath(root)
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
}
