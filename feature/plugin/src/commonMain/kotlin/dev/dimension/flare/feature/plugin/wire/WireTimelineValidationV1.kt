package dev.dimension.flare.feature.plugin.wire

import dev.dimension.flare.media.UgoiraFrame
import dev.dimension.flare.media.UgoiraMetadata

public fun TimelineItemV1.requireValid(depth: Int = 0) {
    require(depth <= 4) { "Timeline nesting is too deep" }
    when (this) {
        is TimelineItemV1.Post -> {
            post.requireValid()
            presentation.message?.requireValid()
            presentation.notificationKey?.requireValid()
            require(presentation.inlineParents.size <= 100 && presentation.quotes.size <= 100) { "Too many presented posts" }
            presentation.inlineParents.forEach { it.requireValid(depth + 1) }
            presentation.quotes.forEach { it.requireValid() }
            presentation.repost?.requireValid()
        }

        is TimelineItemV1.Feed -> {
            feed.requireValid()
        }

        is TimelineItemV1.User -> {
            user.key.requireValid()
            user.createdAt.requireTimestamp()
            user.profile.requireValid()
            user.message?.requireValid()
            user.buttons.requireValid()
            require(user.buttons.all { it.action in USER_ACTIONS_V1 && !it.label.isNullOrBlank() }) { "Invalid user button" }
        }

        is TimelineItemV1.UserList -> {
            userList.key.requireValid()
            userList.createdAt.requireTimestamp()
            require(userList.users.isNotEmpty() && userList.users.size <= 100) { "Invalid user list size" }
            userList.users.forEach { it.requireValid() }
            userList.message?.requireValid()
            userList.post?.requireValid()
        }

        is TimelineItemV1.Message -> {
            message.requireValid()
        }
    }
}

internal val USER_ACTIONS_V1: Set<SemanticActionV1> =
    setOf(
        SemanticActionV1.Follow,
        SemanticActionV1.Unfollow,
        SemanticActionV1.Block,
        SemanticActionV1.Unblock,
        SemanticActionV1.Mute,
        SemanticActionV1.Unmute,
        SemanticActionV1.AcceptFollowRequest,
        SemanticActionV1.RejectFollowRequest,
    )

internal fun List<String>.requireLanguages() {
    require(size <= 32 && all { it.isNotBlank() && it.length <= 64 && it.none(Char::isISOControl) }) { "Invalid languages" }
}

internal fun String?.requireActionToken() {
    require(this == null || length <= WireLimitsV1.MAX_ACTION_TOKEN_LENGTH) { "Action token is too large" }
}

public fun FeedV1.requireValid() {
    url.requireHttpsUrl(required = true)
    createdAt.requireTimestamp()
    require((title?.length ?: 0) <= 16_384) { "Feed title is too long" }
    description?.requireValid()
    require(source.name.isNotBlank() && source.name.length <= 1_024) { "Invalid feed source" }
    source.iconUrl.requireHttpsUrl()
    media?.let {
        require(it.type == MediaTypeV1.Image) { "Feed media must be an image" }
        it.requireValid()
    }
    sourceLanguages.requireLanguages()
    target?.requireValid()
}

public fun MessageV1.requireValid() {
    key.requireValid()
    createdAt.requireTimestamp()
    actor?.requireValid()
    require(listOf(text, kind, unknownType).count { it != null } == 1) { "Message requires exactly one of text, kind, unknownType" }
    require((text?.length ?: 0) <= WireLimitsV1.MAX_RICH_TEXT_LENGTH && (unknownType?.length ?: 0) <= 1_024) { "Message is too long" }
    require(args.size <= 16 && args.all { it.length <= 1_024 }) { "Invalid message arguments" }
    target?.requireValid()
}

public fun NavigationTargetV1.requireValid() {
    when (this) {
        is NavigationTargetV1.Post -> key.requireValid()
        is NavigationTargetV1.Profile -> key.requireValid()
        is NavigationTargetV1.Article -> key.requireValid()
        is NavigationTargetV1.Gallery -> key.requireValid()
        is NavigationTargetV1.Link -> url.requireHttpsUrl(required = true)
    }
}

public fun PollV1.requireValid() {
    require(id.isNotBlank() && id.length <= 512) { "Invalid poll ID" }
    require(options.size in 2..100 && options.all { it.title.length <= 4_096 && it.votesCount >= 0 }) { "Invalid poll options" }
    require(ownVotes.distinct().size == ownVotes.size && ownVotes.all { it in options.indices }) { "Invalid own votes" }
    require(multiple || ownVotes.size <= 1) { "Single-choice poll has multiple votes" }
    expiresAt?.requireTimestamp()
    actionToken.requireActionToken()
}

public fun UgoiraMetadataV1.requireValid() {
    zipUrl.requireHttpsUrl(required = true)
    UgoiraMetadata(zipUrl, frames.map { UgoiraFrame(it.file, it.delayMillis) }).validate()
}

public fun MutationResultV1.requireUserMutationResult(expectedKey: EntityKeyV1) {
    requireValid()
    expectedKey.requireValid()
    when (this) {
        is MutationResultV1.UpdatedProfile -> {
            require(profile.key == expectedKey) { "User action returned a different profile" }
            require(
                profile.actions.all { it.action in USER_ACTIONS_V1 && !it.label.isNullOrBlank() },
            ) { "Invalid replacement user buttons" }
        }

        is MutationResultV1.UpdatedRelation -> {
            require(relation.profileKey == expectedKey) { "User action returned a different relation" }
        }

        MutationResultV1.Deleted, MutationResultV1.NoChange -> {}

        else -> {
            error("User action returned an incompatible result")
        }
    }
}
