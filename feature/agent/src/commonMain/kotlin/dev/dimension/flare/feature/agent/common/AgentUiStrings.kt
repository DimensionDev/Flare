package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.model.PlatformType

internal object AgentUiStrings {
    fun text(
        key: AgentLocalizedTextKey,
        vararg args: String,
    ): AgentLocalizedText = AgentLocalizedText(key = key, args = args.toList())

    object Common {
        const val Cancel = "Cancel"
        const val ConfirmExecute = "Confirm"
        const val CancelExecute = "Cancel execution"
        const val Account = "Account"
        const val Platform = "Platform"
        const val TargetPost = "Target post"
        const val TargetUser = "Target user"
        const val Author = "Author"
        const val ContentSummary = "Summary"
        const val DisplayName = "Display name"
        const val Content = "Content"

        fun replyToConfirm(actionLabel: String): String = "Confirm with \"$actionLabel\"."
    }

    object Subscription {
        const val LoadSourcePrefix = "Load this subscription source:"
        const val DeleteSourcePrefix = "Delete this subscription source:"
        const val SaveSourcePrefix = "Save this subscription source:"
        const val ConfirmSave = "Save"
        const val CancelSave = "Cancel save"
        const val ConfirmDelete = "Delete"
        const val CancelDelete = "Cancel delete"
    }

    object Compose {
        const val SendVerb = "send"
        const val ReplyVerb = "reply to"
        const val QuoteVerb = "quote"
        const val SendConfirmationTitle = "Send this content with the selected account?"
        const val ReplyConfirmationTitle = "Reply to this post with the selected account?"
        const val QuoteConfirmationTitle = "Quote this post with the selected account?"
        const val ConfirmSend = "Send"
        const val CancelSend = "Cancel send"

        fun targetPostPrefix(verb: String): String = "$verb this post:"

        fun accountPrefix(verb: String): String = "Use this account to $verb:"

        fun platformPrefix(verb: String): String = "Use this platform to $verb:"

        fun platformLabel(
            platformType: PlatformType,
            accountCount: Int,
        ): String =
            buildString {
                append(
                    when (platformType) {
                        PlatformType.xQt -> "Twitter/X"
                        PlatformType.VVo -> "Weibo"
                        else -> platformType.name
                    },
                )
                if (accountCount > 1) {
                    append(" (")
                    append(accountCount)
                    append(" accounts)")
                }
            }
    }

    object PostAction {
        const val OperatePostPrefix = "Operate on this post:"
        const val ExecutePostActionPrefix = "Execute an action on this post:"
    }

    object Relation {
        const val RelationStateUserPrefix = "Inspect this user's relationship with an account:"
        const val ExecuteRelationUserPrefix = "Execute a relationship action on this user:"
        const val ExecuteActionPrefix = "Execute a relationship action on this user:"
        const val AccountPrefix = "Use this account for the relationship action:"
    }

    object StatusInsight {
        const val RecentPostsUserPrefix = "View recent posts from this user:"
        const val MatchedUserPrefix = "Select this user to continue the previous request:"
        const val ProfileUserPrefix = "View this user's profile:"
        const val FollowingUserPrefix = "View who this user follows:"
        const val FollowersUserPrefix = "View this user's followers:"
        const val ProfileTabsUserPrefix = "View this user's profile tabs:"
    }
}
