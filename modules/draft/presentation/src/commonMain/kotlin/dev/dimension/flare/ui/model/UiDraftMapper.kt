package dev.dimension.flare.ui.model

import dev.dimension.flare.data.draft.toComposeData
import dev.dimension.flare.data.draft.toUiDraftStatus
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.draft.DraftGroup
import dev.dimension.flare.model.draft.DraftMedia
import dev.dimension.flare.model.draft.DraftMediaType
import dev.dimension.flare.ui.render.toUi
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList

internal fun DraftGroup.toUiDraft(accountProvider: (MicroBlogKey) -> UiDraftAccount?): UiDraft? {
    val accounts =
        targets
            .mapNotNull { target -> accountProvider(target.accountKey) }
            .toImmutableList()
    if (accounts.isEmpty()) {
        return null
    }
    return UiDraft(
        groupId = groupId,
        status = toUiDraftStatus(),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt).toUi(),
        accounts = accounts,
        data = content.toComposeData(medias = emptyList()),
        medias = medias.map { it.toUiDraftMedia() }.toImmutableList(),
    )
}

private fun DraftMedia.toUiDraftMedia(): UiDraftMedia =
    UiDraftMedia(
        cachePath = cachePath,
        fileName = fileName,
        type =
            when (mediaType) {
                DraftMediaType.IMAGE -> UiDraftMediaType.IMAGE
                DraftMediaType.VIDEO -> UiDraftMediaType.VIDEO
                DraftMediaType.OTHER -> UiDraftMediaType.OTHER
            },
        altText = altText,
    )
