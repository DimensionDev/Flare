package dev.dimension.flare.model.draft

import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlin.uuid.Uuid

public data class DraftGroup(
    public val groupId: String,
    public val content: DraftContent,
    public val createdAt: Long,
    public val updatedAt: Long,
    public val targets: List<DraftTarget>,
    public val medias: List<DraftMedia>,
)

public data class DraftContent(
    public val text: String,
    public val visibility: DraftVisibility,
    public val language: List<String>,
    public val sensitive: Boolean,
    public val spoilerText: String? = null,
    public val localOnly: Boolean = false,
    public val poll: DraftPoll? = null,
    public val reference: DraftReference? = null,
)

public data class DraftPoll(
    public val options: List<String>,
    public val expiredAfter: Long,
    public val multiple: Boolean,
)

public data class DraftReference(
    public val type: DraftReferenceType,
    public val statusKey: MicroBlogKey,
    public val rootId: String? = null,
)

public enum class DraftReferenceType {
    REPLY,
    QUOTE,
    VVO_COMMENT,
}

public enum class DraftVisibility {
    Public,
    Home,
    Followers,
    Specified,
    Channel,
}

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

public enum class DraftTargetStatus {
    DRAFT,
    SENDING,
    FAILED,
}

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

public enum class DraftMediaType {
    IMAGE,
    VIDEO,
    OTHER,
}

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
