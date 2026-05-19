package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelinePersistenceMapper
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.data.model.tab.TimelinePresentation
import dev.dimension.flare.data.datasource.microblog.timeline.CommonTimelineSpecs
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineCatalog
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.toTimelineTabDescriptor
import dev.dimension.flare.data.datasource.rss.RssTimelineSpecs
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
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelinePresenterBindingTest {
    private val timelinePersistenceMapper =
        TimelinePersistenceMapper(
            catalog =
                TimelineCatalog(
                    defaultSocialPlatformRegistry.specs.flatMap { it.timelineSpecs } + RssTimelineSpecs.timelineSpecs,
                ),
        )
    private lateinit var root: Path
    private lateinit var appDataStore: AppDataStore

    @BeforeTest
    fun setup() {
        root = createTestRootPath()
        appDataStore = AppDataStore(OkioFileStorage(FileSystem.SYSTEM, root))
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
            appDataStore.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(firstSlot))
            }

            val timelineTabItemIdFlow = MutableStateFlow<String?>(null)
            val observed = mutableListOf<List<TimelinePostKind>>()
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    observeTimelineFilterConfig(
                        appDataStore = appDataStore,
                        timelinePersistenceMapper = timelinePersistenceMapper,
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
    ) = timelinePersistenceMapper
        .toSlot(
            CommonTimelineSpecs.home.toTimelineTabDescriptor(
                TimelineSpec.AccountBasedData(MicroBlogKey(id = "home", host = "example.com")),
            ),
        ).copy(
            id = id,
            presentation = TimelinePresentation(filterConfig = filterConfig),
        )
}
