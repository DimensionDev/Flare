package dev.dimension.flare.feature.plugin.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class TimelinePageRequestV1(
    val timelineId: String,
    val page: PageRequestV1,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
public data class SearchRequestV1(
    val query: String,
    val page: PageRequestV1,
)

@Serializable
public data class EntityRequestV1(
    val key: EntityKeyV1,
    val entityToken: String? = null,
)

@Serializable
public data class HandleRequestV1(
    val handle: String,
    val host: String,
)

@Serializable
public data class ProfileTimelineRequestV1(
    val profile: EntityKeyV1,
    val tabId: String? = null,
    val page: PageRequestV1,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
public data class MutationRequestV1(
    val key: EntityKeyV1,
    val action: SemanticActionV1,
    val actionToken: String? = null,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
public sealed interface MutationResultV1 {
    @Serializable
    @SerialName("updatedPost")
    public data class UpdatedPost(
        val post: PostV1,
    ) : MutationResultV1

    @Serializable
    @SerialName("updatedProfile")
    public data class UpdatedProfile(
        val profile: ProfileV1,
    ) : MutationResultV1

    @Serializable
    @SerialName("updatedRelation")
    public data class UpdatedRelation(
        val relation: RelationV1,
    ) : MutationResultV1

    @Serializable
    @SerialName("deleted")
    public data object Deleted : MutationResultV1

    @Serializable
    @SerialName("invalidate")
    public data class Invalidate(
        val keys: List<EntityKeyV1>,
    ) : MutationResultV1

    @Serializable
    @SerialName("noChange")
    public data object NoChange : MutationResultV1
}

@Serializable
public data class ListMutationRequestV1(
    val id: String? = null,
    val title: String? = null,
    val entityToken: String? = null,
)

@Serializable
public data class ListMemberRequestV1(
    val listId: String,
    val profileKey: EntityKeyV1,
    val entityToken: String? = null,
)

@Serializable
public data class DirectMessageSendRequestV1(
    val roomKey: EntityKeyV1,
    val text: String,
)
