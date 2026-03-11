package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.ui.model.UiDraft
import dev.dimension.flare.ui.model.UiDraftAccount
import dev.dimension.flare.ui.model.UiDraftMedia
import dev.dimension.flare.ui.model.UiDraftMediaType
import dev.dimension.flare.ui.model.UiDraftStatus
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Instant

public class RestoreDraftUseCase internal constructor(
    private val draftRepository: DraftRepository,
    private val accountRepository: AccountRepository,
) {
    public suspend operator fun invoke(groupId: String): UiDraft? {
        val draft = draftRepository.draft(groupId).firstOrNull() ?: return null
        val accounts =
            draft.targets.mapNotNull { target ->
                accountRepository.find(target.accountKey)?.let {
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

internal fun DraftContent.toComposeData(
    medias: List<dev.dimension.flare.data.datasource.microblog.ComposeData.Media>,
): dev.dimension.flare.data.datasource.microblog.ComposeData =
    dev.dimension.flare.data.datasource.microblog.ComposeData(
        content = text,
        visibility = visibility,
        language = language,
        medias = medias,
        sensitive = sensitive,
        spoilerText = spoilerText,
        poll =
            poll?.let {
                dev.dimension.flare.data.datasource.microblog.ComposeData.Poll(
                    options = it.options,
                    expiredAfter = it.expiredAfter,
                    multiple = it.multiple,
                )
            },
        localOnly = localOnly,
        referenceStatus =
            reference?.let { reference ->
                dev.dimension.flare.data.datasource.microblog.ComposeData.ReferenceStatus(
                    composeStatus = reference.toComposeStatus(),
                )
            },
    )

internal fun DraftContent.DraftReference.toComposeStatus(): ComposeStatus =
    when (type) {
        dev.dimension.flare.data.database.app.model.DraftReferenceType.QUOTE -> ComposeStatus.Quote(statusKey)
        dev.dimension.flare.data.database.app.model.DraftReferenceType.REPLY -> ComposeStatus.Reply(statusKey)
        dev.dimension.flare.data.database.app.model.DraftReferenceType.VVO_COMMENT ->
            ComposeStatus.VVOComment(
                statusKey = statusKey,
                rootId = requireNotNull(rootId),
            )
    }

private fun dev.dimension.flare.data.repository.DraftGroup.toUiDraftStatus(): UiDraftStatus =
    when {
        targets.any { it.status == dev.dimension.flare.data.database.app.model.DraftTargetStatus.SENDING } -> UiDraftStatus.SENDING
        targets.any { it.status == dev.dimension.flare.data.database.app.model.DraftTargetStatus.FAILED } -> UiDraftStatus.FAILED
        else -> UiDraftStatus.DRAFT
    }
