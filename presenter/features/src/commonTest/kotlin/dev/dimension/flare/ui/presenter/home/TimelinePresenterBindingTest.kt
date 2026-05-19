package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.data.model.tab.TimelinePresentation
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.toSlot
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.defaultSocialPlatformRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val timelineResolver = TimelineResolver(defaultSocialPlatformRegistry)
    private lateinit var root: Path
    private lateinit var settingsRepository: SettingsRepository

    @BeforeTest
    fun setup() {
        root = createTestRootPath()
        val pathProducer =
            object : PlatformPathProducer {
                override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

                override fun draftMediaFile(
                    groupId: String,
                    fileName: String,
                ): Path = root.resolve(groupId).resolve(fileName)
            }
        settingsRepository =
            SettingsRepository(
                pathProducer = pathProducer,
                appDataStore = AppDataStore(pathProducer),
            )
    }

    @AfterTest
    fun tearDown() {
        deleteTestRootPath(root)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun filterConfigObservationUpdatesWhenIdIsBoundAfterFlowCreation() =
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

            val timelineTabItemIdFlow = MutableStateFlow<String?>(null)
            val observed = mutableListOf<List<TimelinePostKind>>()
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    observeTimelineFilterConfig(
                        settingsRepository = settingsRepository,
                        timelineResolver = timelineResolver,
                        timelineTabItemIdFlow = timelineTabItemIdFlow,
                    ).map { it.excludedKinds }
                        .take(2)
                        .collect(observed::add)
                }
            advanceUntilIdle()
            timelineTabItemIdFlow.value = firstSlot.id
            advanceUntilIdle()
            job.join()

            assertEquals(
                listOf(
                    emptyList(),
                    listOf(TimelinePostKind.Reply),
                ),
                observed,
            )
        }

    private fun homeSlot(
        id: String,
        filterConfig: TimelineFilterConfig,
    ) = CommonTimelineSpecs
        .home
        .target(TimelineSpec.AccountBasedData(MicroBlogKey(id = "home", host = "example.com")))
        .toSlot()
        .copy(
            id = id,
            presentation = TimelinePresentation(filterConfig = filterConfig),
        )
}
