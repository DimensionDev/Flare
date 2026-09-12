package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.UiDateTime
import kotlinx.collections.immutable.ImmutableList

@Immutable
public data class UiOutboxPost(
    val groupId: String,
    val status: UiOutboxStatus,
    val updatedAt: UiDateTime,
    val targets: ImmutableList<UiOutboxTarget>,
    val data: ComposeData,
    val medias: ImmutableList<UiDraftMedia>,
    val progressCurrent: Int,
    val progressMax: Int,
) {
    public val key: String
        get() = "outbox_$groupId"
}

@Immutable
public data class UiOutboxTarget(
    val account: UiAccount,
    val avatar: UiMedia.Image? = null,
    val status: UiOutboxStatus,
    val progressCurrent: Int,
    val progressMax: Int,
    val errorMessage: String? = null,
    val remotePostKey: MicroBlogKey? = null,
)

@Immutable
public enum class UiOutboxStatus {
    SENDING,
    FAILED,
    SENT,
}
