package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiStatus

sealed interface ComposeData

data class BlueskyComposeData(
    val account: UiAccount.Bluesky,
    val content: String,
    val inReplyToID: String? = null,
    val quoteId: String? = null,
    val language: List<String> = listOf("en"),
    val medias: List<FileItem> = emptyList(),
) : ComposeData

data class MastodonComposeData(
    val account: UiAccount.Mastodon,
    val content: String,
    val visibility: UiStatus.Mastodon.Visibility = UiStatus.Mastodon.Visibility.Public,
    val inReplyToID: String? = null,
    val medias: List<FileItem> = emptyList(),
    val sensitive: Boolean = false,
    val spoilerText: String? = null,
    val poll: Poll? = null,
) : ComposeData {
    data class Poll(
        val options: List<String>,
        val expiresIn: Long,
        val multiple: Boolean,
    )
}

data class MisskeyComposeData(
    val account: UiAccount.Misskey,
    val content: String,
    val visibility: UiStatus.Misskey.Visibility = UiStatus.Misskey.Visibility.Public,
    val inReplyToID: String? = null,
    val renoteId: String? = null,
    val medias: List<FileItem> = emptyList(),
    val sensitive: Boolean = false,
    val spoilerText: String? = null,
    val poll: Poll? = null,
    val localOnly: Boolean = false,
) : ComposeData {
    data class Poll(
        val options: List<String>,
        val expiredAfter: Long,
        val multiple: Boolean,
    )
}

data class XQTComposeData(
    val account: UiAccount.XQT,
    val content: String,
    val inReplyToID: String? = null,
    val quoteId: String? = null,
    val quoteUsername: String? = null,
    val medias: List<FileItem> = emptyList(),
    val sensitive: Boolean = false,
    val poll: Poll? = null,
) : ComposeData {
    data class Poll(
        val options: List<String>,
        val expiredAfter: Long,
        val multiple: Boolean,
    )
}
