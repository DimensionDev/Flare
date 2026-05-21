package dev.dimension.flare.data.draft

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbDraftGroup
import dev.dimension.flare.data.database.app.model.DbDraftGroupWithRelations
import dev.dimension.flare.data.database.app.model.DbDraftMedia
import dev.dimension.flare.data.database.app.model.DbDraftTarget
import dev.dimension.flare.data.database.app.model.DraftContent as DbDraftContent
import dev.dimension.flare.data.database.app.model.DraftMediaType as DbDraftMediaType
import dev.dimension.flare.data.database.app.model.DraftReferenceType as DbDraftReferenceType
import dev.dimension.flare.data.database.app.model.DraftTargetStatus as DbDraftTargetStatus
import dev.dimension.flare.data.database.app.model.DraftVisibility as DbDraftVisibility
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.draft.DraftContent
import dev.dimension.flare.model.draft.DraftGroup
import dev.dimension.flare.model.draft.DraftMedia
import dev.dimension.flare.model.draft.DraftMediaType
import dev.dimension.flare.model.draft.DraftPoll
import dev.dimension.flare.model.draft.DraftReference
import dev.dimension.flare.model.draft.DraftReferenceType
import dev.dimension.flare.model.draft.DraftTarget
import dev.dimension.flare.model.draft.DraftTargetStatus
import dev.dimension.flare.model.draft.DraftVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

public class DraftRepository(
    private val database: AppDatabase,
    private val draftMediaStore: DraftMediaStore,
) {
    public val visibleDrafts: Flow<List<DraftGroup>> =
        database
            .draftDao()
            .visibleDraftGroups()
            .map { drafts -> drafts.map { it.toModel() } }

    public val sendingDrafts: Flow<List<DraftGroup>> =
        database
            .draftDao()
            .sendingDraftGroups()
            .map { drafts -> drafts.map { it.toModel() } }

    public fun draft(groupId: String): Flow<DraftGroup?> =
        database
            .draftDao()
            .draftGroup(groupId)
            .map { it?.toModel() }

    public suspend fun saveDraft(input: SaveDraftInput): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val groupId = input.groupId
        val createdAt = input.createdAt ?: database.draftDao().getGroup(groupId)?.created_at ?: now

        database.connect {
            database.draftDao().upsertGroup(
                DbDraftGroup(
                    group_id = groupId,
                    content = input.content,
                    created_at = createdAt,
                    updated_at = now,
                ),
            )
            database.draftDao().deleteTargetsByGroup(groupId)
            if (input.targets.isNotEmpty()) {
                database.draftDao().insertTargets(
                    input.targets.map { target ->
                        DbDraftTarget(
                            group_id = groupId,
                            account_key = target.accountKey,
                            status = target.status,
                            error_message = target.errorMessage,
                            attempt_count = target.attemptCount,
                            last_attempt_at = target.lastAttemptAt,
                            created_at = target.createdAt ?: now,
                            updated_at = now,
                        )
                    },
                )
            }
            database.draftDao().deleteMediasByGroup(groupId)
            if (input.medias.isNotEmpty()) {
                database.draftDao().insertMedias(
                    input.medias.mapIndexed { index, media ->
                        DbDraftMedia(
                            group_id = groupId,
                            cache_path = media.cachePath,
                            file_name = media.fileName,
                            media_type = media.mediaType,
                            alt_text = media.altText,
                            sort_order = media.sortOrder ?: index,
                            created_at = media.createdAt ?: now,
                        )
                    },
                )
            }
        }
        return groupId
    }

    public suspend fun updateTargetStatus(
        groupId: String,
        accountKey: MicroBlogKey,
        status: DbDraftTargetStatus,
        errorMessage: String? = null,
        attemptCount: Int = 0,
        lastAttemptAt: Long? = null,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        database.connect {
            database.draftDao().updateTargetStatus(
                targetId = targetId(groupId, accountKey),
                status = status,
                errorMessage = errorMessage,
                attemptCount = attemptCount,
                lastAttemptAt = lastAttemptAt,
                updatedAt = now,
            )
            database.draftDao().touchGroup(groupId = groupId, updatedAt = now)
        }
    }

    public suspend fun deleteTarget(
        groupId: String,
        accountKey: MicroBlogKey,
    ) {
        database.connect {
            database.draftDao().deleteTarget(targetId(groupId, accountKey))
            if (database.draftDao().countTargets(groupId) == 0) {
                val draft = database.draftDao().getDraftGroup(groupId)
                database.draftDao().deleteGroup(groupId)
                draftMediaStore.delete(draft?.toModel()?.medias.orEmpty())
            } else {
                database.draftDao().touchGroup(
                    groupId = groupId,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                )
            }
        }
    }

    public suspend fun deleteGroup(groupId: String) {
        val medias =
            database
                .draftDao()
                .getDraftGroup(groupId)
                ?.toModel()
                ?.medias
                .orEmpty()
        database.draftDao().deleteGroup(groupId)
        draftMediaStore.delete(medias)
    }

    public suspend fun markSendingAsDraftIfExpired(
        expiredBefore: Long,
        errorMessage: String? = null,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        database.connect {
            database.draftDao().resetSendingTargets(
                fromStatus = DbDraftTargetStatus.SENDING,
                toStatus = DbDraftTargetStatus.DRAFT,
                expiredBefore = expiredBefore,
                errorMessage = errorMessage,
                updatedAt = now,
            )
            database.draftDao().touchExpiredSendingGroups(
                fromStatus = DbDraftTargetStatus.SENDING,
                expiredBefore = expiredBefore,
                updatedAt = now,
            )
        }
    }

    public suspend fun markSendingAsFailed(errorMessage: String? = null) {
        val now = Clock.System.now().toEpochMilliseconds()
        database.connect {
            database.draftDao().touchGroupsByTargetStatus(
                status = DbDraftTargetStatus.SENDING,
                updatedAt = now,
            )
            database.draftDao().updateTargetsByStatus(
                fromStatus = DbDraftTargetStatus.SENDING,
                toStatus = DbDraftTargetStatus.FAILED,
                errorMessage = errorMessage,
                updatedAt = now,
            )
        }
    }

    private fun targetId(
        groupId: String,
        accountKey: MicroBlogKey,
    ) = "${groupId}_$accountKey"
}

public data class SaveDraftInput(
    public val groupId: String,
    public val content: DbDraftContent,
    public val targets: List<SaveDraftTarget>,
    public val medias: List<SaveDraftMedia>,
    public val createdAt: Long? = null,
)

public data class SaveDraftTarget(
    public val accountKey: MicroBlogKey,
    public val status: DbDraftTargetStatus = DbDraftTargetStatus.DRAFT,
    public val errorMessage: String? = null,
    public val attemptCount: Int = 0,
    public val lastAttemptAt: Long? = null,
    public val createdAt: Long? = null,
)

public data class SaveDraftMedia(
    public val cachePath: String,
    public val fileName: String? = null,
    public val mediaType: DbDraftMediaType,
    public val altText: String? = null,
    public val sortOrder: Int? = null,
    public val createdAt: Long? = null,
)

private fun DbDraftGroupWithRelations.toModel(): DraftGroup =
    DraftGroup(
        groupId = group.group_id,
        content = group.content.toModel(),
        createdAt = group.created_at,
        updatedAt = group.updated_at,
        targets =
            targets
                .sortedBy { it.account_key.toString() }
                .map {
                    DraftTarget(
                        groupId = it.group_id,
                        accountKey = it.account_key,
                        status = it.status.toModel(),
                        errorMessage = it.error_message,
                        attemptCount = it.attempt_count,
                        lastAttemptAt = it.last_attempt_at,
                        createdAt = it.created_at,
                        updatedAt = it.updated_at,
                    )
                },
        medias =
            medias
                .sortedBy { it.sort_order }
                .map {
                    DraftMedia(
                        mediaId = it.media_id,
                        groupId = it.group_id,
                        cachePath = it.cache_path,
                        fileName = it.file_name,
                        mediaType = it.media_type.toModel(),
                        altText = it.alt_text,
                        sortOrder = it.sort_order,
                        createdAt = it.created_at,
                    )
                },
    )

private fun DbDraftContent.toModel(): DraftContent =
    DraftContent(
        text = text,
        visibility = visibility.toModel(),
        language = language,
        sensitive = sensitive,
        spoilerText = spoilerText,
        localOnly = localOnly,
        poll =
            poll?.let {
                DraftPoll(
                    options = it.options,
                    expiredAfter = it.expiredAfter,
                    multiple = it.multiple,
                )
            },
        reference =
            reference?.let {
                DraftReference(
                    type = it.type.toModel(),
                    statusKey = it.statusKey,
                    rootId = it.rootId,
                )
            },
    )

private fun DbDraftVisibility.toModel(): DraftVisibility =
    when (this) {
        DbDraftVisibility.Public -> DraftVisibility.Public
        DbDraftVisibility.Home -> DraftVisibility.Home
        DbDraftVisibility.Followers -> DraftVisibility.Followers
        DbDraftVisibility.Specified -> DraftVisibility.Specified
        DbDraftVisibility.Channel -> DraftVisibility.Channel
    }

private fun DbDraftReferenceType.toModel(): DraftReferenceType =
    when (this) {
        DbDraftReferenceType.REPLY -> DraftReferenceType.REPLY
        DbDraftReferenceType.QUOTE -> DraftReferenceType.QUOTE
        DbDraftReferenceType.VVO_COMMENT -> DraftReferenceType.VVO_COMMENT
    }

private fun DbDraftTargetStatus.toModel(): DraftTargetStatus =
    when (this) {
        DbDraftTargetStatus.DRAFT -> DraftTargetStatus.DRAFT
        DbDraftTargetStatus.SENDING -> DraftTargetStatus.SENDING
        DbDraftTargetStatus.FAILED -> DraftTargetStatus.FAILED
    }

private fun DbDraftMediaType.toModel(): DraftMediaType =
    when (this) {
        DbDraftMediaType.IMAGE -> DraftMediaType.IMAGE
        DbDraftMediaType.VIDEO -> DraftMediaType.VIDEO
        DbDraftMediaType.OTHER -> DraftMediaType.OTHER
    }
