package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Serializable

@Serializable
public data class StatusMutation(
    val statusKey: MicroBlogKey,
    val accountKey: MicroBlogKey,
    val type: String,
    val params: Map<String, String> = emptyMap(),
) {
    public companion object {
        // Common action types
        public const val TYPE_LIKE: String = "like"
        public const val TYPE_REPOST: String = "repost"
        public const val TYPE_BOOKMARK: String = "bookmark"
        public const val TYPE_REACT: String = "react"
        public const val TYPE_FAVOURITE: String = "favourite"
        public const val TYPE_LIKE_COMMENT: String = "like_comment"
        public const val TYPE_REPORT: String = "report"
        public const val TYPE_VOTE: String = "vote"
        public const val TYPE_ACCEPT_FOLLOW_REQUEST: String = "accept_follow_request"
        public const val TYPE_REJECT_FOLLOW_REQUEST: String = "reject_follow_request"

        // Common param keys
        public const val PARAM_TOGGLED: String = "toggled"
        public const val PARAM_COUNT: String = "count"
        public const val PARAM_POLL_ID: String = "poll_id"
        public const val PARAM_OPTIONS: String = "options"
        public const val PARAM_USER_KEY: String = "user_key"
        public const val PARAM_NOTIFICATION_STATUS_KEY: String = "notification_status_key"
        public const val PARAM_REACTION: String = "reaction"
        public const val PARAM_HAS_REACTED: String = "has_reacted"
    }
}

public val StatusMutation.toggled: Boolean
    get() = params[StatusMutation.PARAM_TOGGLED]?.toBooleanStrictOrNull() ?: false

public val StatusMutation.count: Long
    get() = params[StatusMutation.PARAM_COUNT]?.toLongOrNull() ?: 0
