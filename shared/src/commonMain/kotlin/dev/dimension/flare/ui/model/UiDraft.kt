package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.UiDateTime
import kotlinx.collections.immutable.ImmutableList

@Immutable
public data class UiDraft(
    val groupId: String,
    val status: UiDraftStatus,
    val updatedAt: UiDateTime,
    val accounts: ImmutableList<UiDraftAccount>,
    val data: ComposeData,
    val medias: ImmutableList<UiDraftMedia>,
)

@Immutable
public data class UiDraftAccount(
    val account: UiAccount,
    val avatar: String? = null,
)

@Immutable
public data class UiDraftMedia(
    val cachePath: String,
    val fileName: String?,
    val type: UiDraftMediaType,
    val altText: String?,
)

@Immutable
public enum class UiDraftMediaType {
    IMAGE,
    VIDEO,
    OTHER,
}

@Immutable
public enum class UiDraftStatus {
    SENDING,
    FAILED,
    DRAFT,
}

public val UiDraft.primaryAccountKey: MicroBlogKey?
    get() = accounts.firstOrNull()?.account?.accountKey
