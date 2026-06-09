package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.Serializable

internal sealed interface AgentConversationEvent<out Content, out Trace> {
    data class ContentLoaded<Content>(
        val content: Content,
    ) : AgentConversationEvent<Content, Nothing>

    data class Trace<Trace>(
        val trace: Trace,
    ) : AgentConversationEvent<Nothing, Trace>

    data class Result(
        val text: String,
        val attachments: List<AgentConversationAttachment> = emptyList(),
        val inputRequest: AgentInputRequest? = null,
    ) : AgentConversationEvent<Nothing, Nothing>
}

@Serializable
public data class AgentInputRequest(
    val requestId: String,
    val localizedPrompt: AgentLocalizedText,
    val options: List<Option>,
    val allowFreeText: Boolean = true,
    val localizedFreeTextPlaceholder: AgentLocalizedText? = null,
    val postPreview: UiTimelineV2.Post? = null,
    val userPreview: UiProfile? = null,
) {
    @Serializable
    public data class Option(
        val id: String,
        val localizedLabel: AgentLocalizedText,
        val value: String,
        val submit: Boolean = true,
        val userPreview: UiProfile? = null,
        val postPreview: UiTimelineV2.Post? = null,
    )
}

@Serializable
public data class AgentLocalizedText(
    val key: AgentLocalizedTextKey,
    val args: List<String> = emptyList(),
) {
    internal fun toAgentProtocolText(): String =
        buildString {
            append(key.name)
            if (args.isNotEmpty()) {
                append(": ")
                append(args.joinToString(separator = " | "))
            }
        }
}

@Serializable
public enum class AgentLocalizedTextKey {
    DynamicText,
    Cancel,
    ConfirmExecute,
    ConfirmSaveSubscription,
    CancelSaveSubscription,
    ConfirmDeleteSubscription,
    CancelDeleteSubscription,
    ConfirmSendPost,
    CancelSendPost,
    SelectLoadSubscriptionSource,
    SelectDeleteSubscriptionSource,
    SelectSaveSubscriptionSource,
    SubscriptionSourcePlaceholder,
    SubscriptionSaveSelectionPlaceholder,
    SubscriptionSaveConfirmationPlaceholder,
    SubscriptionDeleteConfirmationPlaceholder,
    SubscriptionSaveConfirmationMessage,
    SubscriptionDeleteConfirmationMessage,
    SelectComposeTargetPost,
    SelectComposeAccount,
    SelectComposePlatform,
    ComposeTargetPostPlaceholder,
    ComposeAccountPlaceholder,
    ComposePlatformPlaceholder,
    ComposeConfirmationPlaceholder,
    ComposeSendConfirmationTitle,
    ComposeReplyConfirmationTitle,
    ComposeQuoteConfirmationTitle,
    ComposeConfirmationMessage,
    SelectPostActionPost,
    SelectPostAction,
    PostActionTargetPostPlaceholder,
    PostActionPlaceholder,
    PostActionConfirmationPlaceholder,
    PostActionConfirmationMessage,
    SelectRelationStateUser,
    SelectRelationUser,
    SelectRelationAction,
    SelectRelationAccount,
    RelationUserPlaceholder,
    RelationActionPlaceholder,
    RelationAccountPlaceholder,
    RelationConfirmationPlaceholder,
    RelationConfirmationMessage,
    SelectRecentPostsUser,
    SelectMatchedUser,
    SelectProfileUser,
    SelectFollowingUser,
    SelectFollowersUser,
    SelectProfileTabsUser,
    StatusInsightUserPlaceholder,
}
