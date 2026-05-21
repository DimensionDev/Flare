package dev.dimension.flare.data.draft

import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.model.draft.DraftContent
import dev.dimension.flare.model.draft.DraftReference
import dev.dimension.flare.model.draft.DraftReferenceType
import dev.dimension.flare.model.draft.DraftVisibility
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus

public fun DraftContent.toComposeData(medias: List<ComposeData.Media>): ComposeData =
    ComposeData(
        content = text,
        visibility = visibility.toUiVisibility(),
        language = language,
        medias = medias,
        sensitive = sensitive,
        spoilerText = spoilerText,
        poll =
            poll?.let {
                ComposeData.Poll(
                    options = it.options,
                    expiredAfter = it.expiredAfter,
                    multiple = it.multiple,
                )
            },
        localOnly = localOnly,
        referenceStatus =
            reference?.let { reference ->
                ComposeData.ReferenceStatus(
                    composeStatus = reference.toComposeStatus(),
                )
            },
    )

private fun DraftVisibility.toUiVisibility(): UiTimelineV2.Post.Visibility =
    when (this) {
        DraftVisibility.Public -> UiTimelineV2.Post.Visibility.Public
        DraftVisibility.Home -> UiTimelineV2.Post.Visibility.Home
        DraftVisibility.Followers -> UiTimelineV2.Post.Visibility.Followers
        DraftVisibility.Specified -> UiTimelineV2.Post.Visibility.Specified
        DraftVisibility.Channel -> UiTimelineV2.Post.Visibility.Channel
    }

private fun DraftReference.toComposeStatus(): ComposeStatus =
    when (type) {
        DraftReferenceType.QUOTE -> {
            ComposeStatus.Quote(statusKey)
        }

        DraftReferenceType.REPLY -> {
            ComposeStatus.Reply(statusKey)
        }

        DraftReferenceType.VVO_COMMENT -> {
            ComposeStatus.VVOComment(
                statusKey = statusKey,
                rootId = requireNotNull(rootId),
            )
        }
    }
