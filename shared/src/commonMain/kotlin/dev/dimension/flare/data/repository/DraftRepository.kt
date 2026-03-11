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

internal class DraftRepository(
    private val database: AppDatabase,
    private val draftMediaStore: DraftMediaStore,
) {
    val visibleDrafts: Flow<List<DraftGroup>> =
        database
            .draftDao()
            .visibleDraftGroups()
            .map { drafts -> drafts.map { it.toModel() } }

    val sendingDrafts: Flow<List<DraftGroup>> =
        database
            .draftDao()
            .sendingDraftGroups()
            .map { drafts -> drafts.map { it.toModel() } }

    fun draft(groupId: String): Flow<DraftGroup?> =
        database
            .draftDao()
            .draftGroup(groupId)
            .map { it?.toModel() }

    suspend fun saveDraft(input: SaveDraftInput): String {
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

    suspend fun updateTargetStatus(
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

    suspend fun deleteTarget(
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

    suspend fun deleteGroup(groupId: String) {
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

    suspend fun markSendingAsDraftIfExpired(
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

    private fun targetId(
        groupId: String,
        accountKey: MicroBlogKey,
    ) = "${groupId}_$accountKey"
}

internal data class SaveDraftInput(
    val groupId: String,
    val content: DraftContent,
    val targets: List<SaveDraftTarget>,
    val medias: List<SaveDraftMedia>,
    val createdAt: Long? = null,
)

internal data class SaveDraftTarget(
    val accountKey: MicroBlogKey,
    val status: DraftTargetStatus = DraftTargetStatus.DRAFT,
    val errorMessage: String? = null,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val createdAt: Long? = null,
)

internal data class SaveDraftMedia(
    val cachePath: String,
    val fileName: String? = null,
    val mediaType: DraftMediaType,
    val altText: String? = null,
    val sortOrder: Int? = null,
    val createdAt: Long? = null,
)

internal data class DraftGroup(
    val groupId: String,
    val content: DraftContent,
    val createdAt: Long,
    val updatedAt: Long,
    val targets: List<DraftTarget>,
    val medias: List<DraftMedia>,
)

internal data class DraftTarget(
    val groupId: String,
    val accountKey: MicroBlogKey,
    val status: DraftTargetStatus,
    val errorMessage: String?,
    val attemptCount: Int,
    val lastAttemptAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class DraftMedia(
    val mediaId: String,
    val groupId: String,
    val cachePath: String,
    val fileName: String?,
    val mediaType: DraftMediaType,
    val altText: String?,
    val sortOrder: Int,
    val createdAt: Long,
)

internal data class ComposeDraftBundle(
    val accounts: List<UiAccount>,
    val template: ComposeData,
    val groupId: String = newDraftGroupId(),
)

internal fun ComposeData.toComposeDraftBundle(
    accounts: List<UiAccount>,
    groupId: String = newDraftGroupId(),
): ComposeDraftBundle = ComposeDraftBundle(accounts = accounts, template = this, groupId = groupId)

internal fun newDraftGroupId(): String = Uuid.random().toString()

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
