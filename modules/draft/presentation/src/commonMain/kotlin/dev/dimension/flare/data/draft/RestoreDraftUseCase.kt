package dev.dimension.flare.data.draft

import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDraft
import dev.dimension.flare.ui.model.UiDraftAccount
import dev.dimension.flare.ui.model.UiDraftMedia
import dev.dimension.flare.ui.model.UiDraftMediaType
import dev.dimension.flare.ui.model.UiDraftStatus
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Instant

public class RestoreDraftUseCase(
    private val draftRepository: DraftRepository,
    private val findAccount: suspend (MicroBlogKey) -> UiAccount?,
) {
    public suspend operator fun invoke(groupId: String): UiDraft? {
        val draft = draftRepository.draft(groupId).firstOrNull() ?: return null
        val accounts =
            draft.targets.mapNotNull { target ->
                findAccount(target.accountKey)?.let {
                    UiDraftAccount(account = it)
                }
            }
        return UiDraft(
            groupId = draft.groupId,
            status = draft.toUiDraftStatus(),
            updatedAt = Instant.fromEpochMilliseconds(draft.updatedAt).toUi(),
            accounts = accounts.toImmutableList(),
            data = draft.content.toComposeData(medias = emptyList()),
            medias =
                draft.medias
                    .map { media ->
                        UiDraftMedia(
                            cachePath = media.cachePath,
                            fileName = media.fileName,
                            type =
                                when (media.mediaType) {
                                    DraftMediaType.IMAGE -> UiDraftMediaType.IMAGE
                                    DraftMediaType.VIDEO -> UiDraftMediaType.VIDEO
                                    DraftMediaType.OTHER -> UiDraftMediaType.OTHER
                                },
                            altText = media.altText,
                        )
                    }.toImmutableList(),
        )
    }
}

public fun DraftGroup.toUiDraftStatus(): UiDraftStatus =
    when {
        targets.any { it.status == DraftTargetStatus.SENDING } -> UiDraftStatus.SENDING
        targets.any { it.status == DraftTargetStatus.FAILED } -> UiDraftStatus.FAILED
        else -> UiDraftStatus.DRAFT
    }
