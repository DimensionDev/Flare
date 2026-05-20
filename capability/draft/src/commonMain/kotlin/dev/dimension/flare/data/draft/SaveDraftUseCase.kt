package dev.dimension.flare.data.draft

import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.database.app.model.DraftReferenceType
import dev.dimension.flare.data.database.app.model.DraftVisibility
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus

public class SaveDraftUseCase(
    private val draftRepository: DraftRepository,
    private val draftMediaStore: DraftMediaStore,
) {
    public suspend operator fun invoke(bundle: ComposeDraftBundle): String {
        val persistedMedia = draftMediaStore.persist(groupId = bundle.groupId, medias = bundle.template.medias)
        return draftRepository.saveDraft(
            SaveDraftInput(
                groupId = bundle.groupId,
                content = bundle.template.toDraftContent(),
                targets =
                    bundle.accounts.map {
                        SaveDraftTarget(
                            accountKey = it.accountKey,
                        )
                    },
                medias = persistedMedia,
            ),
        )
    }
}

public fun ComposeData.toDraftContent(): DraftContent =
    DraftContent(
        text = content,
        visibility = visibility.toDraftVisibility(),
        language = language,
        sensitive = sensitive,
        spoilerText = spoilerText,
        localOnly = localOnly,
        poll =
            poll?.let {
                DraftContent.DraftPoll(
                    options = it.options,
                    expiredAfter = it.expiredAfter,
                    multiple = it.multiple,
                )
            },
        reference =
            referenceStatus?.let {
                DraftContent.DraftReference(
                    type =
                        when (it.composeStatus) {
                            is ComposeStatus.Quote -> DraftReferenceType.QUOTE
                            is ComposeStatus.VVOComment -> DraftReferenceType.VVO_COMMENT
                            is ComposeStatus.Reply -> DraftReferenceType.REPLY
                        },
                    statusKey = it.composeStatus.statusKey,
                    rootId = (it.composeStatus as? ComposeStatus.VVOComment)?.rootId,
                )
            },
    )

private fun UiTimelineV2.Post.Visibility.toDraftVisibility(): DraftVisibility =
    when (this) {
        UiTimelineV2.Post.Visibility.Public -> DraftVisibility.Public
        UiTimelineV2.Post.Visibility.Home -> DraftVisibility.Home
        UiTimelineV2.Post.Visibility.Followers -> DraftVisibility.Followers
        UiTimelineV2.Post.Visibility.Specified -> DraftVisibility.Specified
        UiTimelineV2.Post.Visibility.Channel -> DraftVisibility.Channel
    }
