package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datasource.microblog.timeline.AccountTimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.StandaloneTimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineCatalog
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineDisplay
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineTabDescriptor
import dev.dimension.flare.data.datasource.microblog.timeline.toTimelineTabDescriptor
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.AccountTimelinePresenter
import dev.dimension.flare.ui.presenter.home.StandaloneTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class TimelinePresenterFactoryTest {
    private val accountSpec =
        AccountTimelineSpec(
            id = "common.home",
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { _, _ -> notSupported() },
        )
    private val standaloneSpec =
        StandaloneTimelineSpec(
            id = "standalone.fake",
            title = UiStrings.Posts,
            icon = UiIcon.List.asType(),
            serializer = StandaloneData.serializer(),
            stableKeyFactory = { it.value },
            loaderFactory = { _, _ -> flowOf(notSupported()) },
        )
    private val mapper =
        TimelinePersistenceMapper(
            catalog = TimelineCatalog(listOf(accountSpec, standaloneSpec)),
        )
    private val factory = TimelinePresenterFactory(mapper)

    @Test
    fun createsAccountTimelinePresenterFromTypedSourceTab() {
        val item =
            mapper.toTabItem(
                accountSpec.toTimelineTabDescriptor(
                    TimelineSpec.AccountBasedData(MicroBlogKey("home", "example.com")),
                ),
            )

        assertIs<AccountTimelinePresenter>(factory.create(item))
    }

    @Test
    fun createsStandaloneTimelinePresenterFromTypedSourceTab() {
        val item =
            mapper.toTabItem(
                standaloneSpec.toTimelineTabDescriptor(StandaloneData("standalone")),
            )

        assertIs<StandaloneTimelinePresenter>(factory.create(item))
    }

    @Test
    fun createsPresenterFromStoredSourceWhenRuntimeRefIsAbsent() {
        val descriptor =
            accountSpec.toTimelineTabDescriptor(
                TimelineSpec.AccountBasedData(MicroBlogKey("home", "example.com")),
            )
        val source = mapper.toSourceRef(descriptor)
        val slot = mapper.toSlot(descriptor)
        val item = SourceTimelineTabItemV2.fromSlot(slot, source, ref = null)

        assertIs<AccountTimelinePresenter>(factory.create(item))
    }

    @Test
    fun returnsRuntimePresenterForRuntimeOnlyTab() {
        val presenter = FakeTimelinePresenter()
        val item =
            SourceTimelineTabItemV2.runtime(
                id = "runtime:fake",
                title = UiText.Raw("Runtime"),
                icon = UiIcon.Home.asType(),
                runtimePresenterFactory = { presenter },
            )

        assertSame(presenter, factory.create(item))
    }

    @Test
    fun rejectsUnsupportedTimelineSpec() {
        val unsupportedSpec = UnsupportedTimelineSpec()
        val unsupportedMapper =
            TimelinePersistenceMapper(
                catalog = TimelineCatalog(listOf(unsupportedSpec)),
            )
        val unsupportedFactory = TimelinePresenterFactory(unsupportedMapper)
        val item =
            unsupportedMapper.toTabItem(
                TimelineTabDescriptor.Source(
                    ref = unsupportedSpec.ref(StandaloneData("unsupported")),
                    display =
                        TimelineDisplay(
                            title = UiText.Raw("Unsupported"),
                            icon = UiIcon.Home.asType(),
                        ),
                ),
            )

        assertFailsWith<IllegalArgumentException> {
            unsupportedFactory.create(item)
        }
    }

    private class FakeTimelinePresenter : TimelinePresenter() {
        override val loader: Flow<RemoteLoader<UiTimelineV2>> = flowOf(notSupported())
    }

    private class UnsupportedTimelineSpec : TimelineSpec<StandaloneData> {
        override val id: String = "unsupported.fake"
        override val title: UiStrings = UiStrings.Posts
        override val icon = UiIcon.Home.asType()
        override val serializer = StandaloneData.serializer()

        override fun stableKey(data: StandaloneData): String = data.value
    }

    @Serializable
    private data class StandaloneData(
        val value: String,
    ) : TimelineSpec.Data
}
