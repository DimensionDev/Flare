package dev.dimension.flare.data.repository

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbDraftGroup
import dev.dimension.flare.data.database.app.model.DbDraftGroupWithRelations
import dev.dimension.flare.data.database.app.model.DbDraftMedia
import dev.dimension.flare.data.database.app.model.DbDraftTarget
import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.Uuid

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
        status: DraftTargetStatus,
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
                fromStatus = DraftTargetStatus.SENDING,
                toStatus = DraftTargetStatus.DRAFT,
                expiredBefore = expiredBefore,
                errorMessage = errorMessage,
                updatedAt = now,
            )
            database.draftDao().touchExpiredSendingGroups(
                fromStatus = DraftTargetStatus.SENDING,
                expiredBefore = expiredBefore,
                updatedAt = now,
            )
        }
    }

    public suspend fun markSendingAsFailed(errorMessage: String? = null) {
        val now = Clock.System.now().toEpochMilliseconds()
        database.connect {
            database.draftDao().touchGroupsByTargetStatus(
                status = DraftTargetStatus.SENDING,
                updatedAt = now,
            )
            database.draftDao().updateTargetsByStatus(
                fromStatus = DraftTargetStatus.SENDING,
                toStatus = DraftTargetStatus.FAILED,
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
    public val content: DraftContent,
    public val targets: List<SaveDraftTarget>,
    public val medias: List<SaveDraftMedia>,
    public val createdAt: Long? = null,
)

public data class SaveDraftTarget(
    public val accountKey: MicroBlogKey,
    public val status: DraftTargetStatus = DraftTargetStatus.DRAFT,
    public val errorMessage: String? = null,
    public val attemptCount: Int = 0,
    public val lastAttemptAt: Long? = null,
    public val createdAt: Long? = null,
)

public data class SaveDraftMedia(
    public val cachePath: String,
    public val fileName: String? = null,
    public val mediaType: DraftMediaType,
    public val altText: String? = null,
    public val sortOrder: Int? = null,
    public val createdAt: Long? = null,
)

public data class DraftGroup(
    public val groupId: String,
    public val content: DraftContent,
    public val createdAt: Long,
    public val updatedAt: Long,
    public val targets: List<DraftTarget>,
    public val medias: List<DraftMedia>,
)

public data class DraftTarget(
    public val groupId: String,
    public val accountKey: MicroBlogKey,
    public val status: DraftTargetStatus,
    public val errorMessage: String?,
    public val attemptCount: Int,
    public val lastAttemptAt: Long?,
    public val createdAt: Long,
    public val updatedAt: Long,
)

public data class DraftMedia(
    public val mediaId: String,
    public val groupId: String,
    public val cachePath: String,
    public val fileName: String?,
    public val mediaType: DraftMediaType,
    public val altText: String?,
    public val sortOrder: Int,
    public val createdAt: Long,
)

public data class ComposeDraftBundle(
    public val accounts: List<UiAccount>,
    public val template: ComposeData,
    public val groupId: String = newDraftGroupId(),
)

public fun ComposeData.toComposeDraftBundle(
    accounts: List<UiAccount>,
    groupId: String = newDraftGroupId(),
): ComposeDraftBundle = ComposeDraftBundle(accounts = accounts, template = this, groupId = groupId)

public fun newDraftGroupId(): String = Uuid.random().toString()

private fun DbDraftGroupWithRelations.toModel(): DraftGroup =
    DraftGroup(
        groupId = group.group_id,
        content = group.content,
        createdAt = group.created_at,
        updatedAt = group.updated_at,
        targets =
            targets
                .sortedBy { it.account_key.toString() }
                .map {
                    DraftTarget(
                        groupId = it.group_id,
                        accountKey = it.account_key,
                        status = it.status,
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
                        mediaType = it.media_type,
                        altText = it.alt_text,
                        sortOrder = it.sort_order,
                        createdAt = it.created_at,
                    )
                },
    )
