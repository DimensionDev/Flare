package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.database.app.model.DraftReferenceType
import dev.dimension.flare.data.repository.ComposeDraftBundle
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.SaveDraftInput
import dev.dimension.flare.data.repository.SaveDraftTarget

internal class SaveDraftUseCase(
    private val draftRepository: DraftRepository,
    private val draftMediaStore: DraftMediaStore,
) {
    suspend operator fun invoke(bundle: ComposeDraftBundle): String {
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

internal fun dev.dimension.flare.data.datasource.microblog.ComposeData.toDraftContent(): DraftContent =
    DraftContent(
        text = content,
        visibility = visibility,
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
