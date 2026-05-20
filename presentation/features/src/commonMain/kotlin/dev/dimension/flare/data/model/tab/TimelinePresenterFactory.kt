package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.datasource.microblog.timeline.AccountTimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.StandaloneTimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineRef
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.ui.presenter.home.AccountTimelinePresenter
import dev.dimension.flare.ui.presenter.home.MixedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.StandaloneTimelinePresenter
import dev.dimension.flare.ui.presenter.home.SystemHomeMixedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter

internal class TimelinePresenterFactory(
    private val timelinePersistenceMapper: TimelinePersistenceMapper,
) {
    fun create(tab: TimelineTabItemV2): TimelinePresenter =
        when (tab) {
            is SourceTimelineTabItemV2 -> create(tab)
            is GroupTimelineTabItemV2 -> create(tab)
        }.also {
            it.bindTimelineTabItemId(tab.id)
        }

    private fun create(tab: SourceTimelineTabItemV2): TimelinePresenter =
        tab.runtimePresenterFactory?.invoke()
            ?: tab.ref?.let(::create)
            ?: tab.source?.let(timelinePersistenceMapper::decode)?.let(::create)
            ?: throw IllegalArgumentException("Runtime timeline tab has no presenter factory: ${tab.id}")

    private fun create(tab: GroupTimelineTabItemV2): TimelinePresenter =
        when (tab.source) {
            GroupSource.SystemHome -> SystemHomeMixedTimelinePresenter(id = tab.id)
            GroupSource.Manual -> MixedTimelinePresenter(id = tab.id)
        }

    private fun create(ref: TimelineRef<out TimelineSpec.Data>): TimelinePresenter =
        when (val spec = ref.spec) {
            is AccountTimelineSpec<*> -> {
                AccountTimelinePresenter(
                    accountKey = spec.accountKey(ref.data),
                    loaderFactory = { service -> spec.createLoader(service, ref.data) },
                )
            }

            is StandaloneTimelineSpec<*> -> {
                StandaloneTimelinePresenter { context ->
                    spec.createLoader(
                        context = context,
                        data = ref.data,
                    )
                }
            }

            else -> {
                throw IllegalArgumentException("Unsupported timeline spec type: ${spec::class}")
            }
        }
}
