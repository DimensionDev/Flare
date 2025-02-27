package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ListsMembershipsResponse(
    @SerialName("lists")
    val lists: List<TwitterList>? = null,
)

@Serializable
internal data class UpdateListRequest(
    val variables: Variables,
    val features: Features = Features(),
    @SerialName("queryId")
    val queryID: String = "hC86V8CK9BF4EkA5Wcq9hQ",
) {
    @Serializable
    data class Features(
        @SerialName("profile_label_improvements_pcf_label_in_post_enabled")
        val profileLabelImprovementsPcfLabelInPostEnabled: Boolean = true,
        @SerialName("rweb_tipjar_consumption_enabled")
        val rwebTipjarConsumptionEnabled: Boolean = true,
        @SerialName("responsive_web_graphql_exclude_directive_enabled")
        val responsiveWebGraphqlExcludeDirectiveEnabled: Boolean = true,
        @SerialName("verified_phone_label_enabled")
        val verifiedPhoneLabelEnabled: Boolean = false,
        @SerialName("responsive_web_graphql_skip_user_profile_image_extensions_enabled")
        val responsiveWebGraphqlSkipUserProfileImageExtensionsEnabled: Boolean = false,
        @SerialName("responsive_web_graphql_timeline_navigation_enabled")
        val responsiveWebGraphqlTimelineNavigationEnabled: Boolean = true,
    )

    @Serializable
    data class Variables(
        @SerialName("listId")
        val listID: String,
        val isPrivate: Boolean,
        val description: String,
        val name: String,
    )
}

@Serializable
internal data class RemoveListResponse(
    @SerialName("data")
    val data: RemoveListResponseData? = null,
)

@Serializable
internal data class RemoveListResponseData(
    @SerialName("list_delete")
    val listDelete: String? = null,
)

@Serializable
internal data class RemoveListRequest(
    val variables: Variables,
    @SerialName("queryId")
    val queryID: String = "UnN9Th1BDbeLjpgjGSpL3Q",
) {
    @Serializable
    data class Variables(
        @SerialName("listId")
        val listID: String,
    )
}

@Serializable
internal data class ListsOwnerships200Response(
    @SerialName("data")
    val data: ListsOwnerships200ResponseData? = null,
)

@Serializable
internal data class ListsOwnerships200ResponseData(
    @SerialName("user")
    val user: ListsOwnerships200ResponseUser? = null,
)

@Serializable
internal data class ListsOwnerships200ResponseUser(
    @SerialName("result")
    val result: ListsOwnerships200ResponseResult? = null,
)

@Serializable
internal data class ListsOwnerships200ResponseResult(
    @SerialName("timeline")
    val timeline: ListsOwnerships200ResponseTimeline? = null,
)

@Serializable
internal data class ListsOwnerships200ResponseTimeline(
    @SerialName("timeline")
    val timeline: Timeline? = null,
)

@Serializable
internal data class ListsMembers200Response(
    @SerialName("data")
    val data: ListsMembers200ResponseData? = null,
)

@Serializable
internal data class ListsMembers200ResponseData(
    @SerialName("list")
    val list: ListsMembers200ResponseList? = null,
)

@Serializable
internal data class ListsMembers200ResponseList(
    @SerialName("members_timeline")
    val membersTimeline: ListsMembers200ResponseMembersTimeline? = null,
)

@Serializable
internal data class ListsMembers200ResponseMembersTimeline(
    @SerialName("timeline")
    val timeline: Timeline? = null,
)

@Serializable
internal data class ListsManagementPageTimeline200Response(
    @SerialName("data")
    val data: ListsManagementPageTimeline200ResponseData? = null,
)

@Serializable
internal data class ListsManagementPageTimeline200ResponseData(
    @SerialName("viewer")
    val viewer: ListsManagementPageTimelineViewer? = null,
)

@Serializable
internal data class ListsManagementPageTimelineViewer(
    @SerialName("list_management_timeline")
    val listManagementTimeline: ListsManagementPageTimelineListManagementTimeline? = null,
)

@Serializable
internal data class ListsManagementPageTimelineListManagementTimeline(
    @SerialName("timeline")
    val timeline: Timeline? = null,
)

@Serializable
internal data class CreateListResponse(
    @SerialName("data")
    val data: CreateListResponseData? = null,
)

@Serializable
internal data class CreateListResponseData(
    @SerialName("list")
    val list: TwitterList,
)

@Serializable
internal data class AddMemberRequest(
    val variables: Variables,
    val features: Features = Features(),
    @SerialName("queryId")
    val queryID: String = "cfIJQu0q_i0WMDzQLa4dRA",
) {
    @Serializable
    data class Features(
        @SerialName("profile_label_improvements_pcf_label_in_post_enabled")
        val profileLabelImprovementsPcfLabelInPostEnabled: Boolean = true,
        @SerialName("rweb_tipjar_consumption_enabled")
        val rwebTipjarConsumptionEnabled: Boolean = true,
        @SerialName("responsive_web_graphql_exclude_directive_enabled")
        val responsiveWebGraphqlExcludeDirectiveEnabled: Boolean = true,
        @SerialName("verified_phone_label_enabled")
        val verifiedPhoneLabelEnabled: Boolean = false,
        @SerialName("responsive_web_graphql_skip_user_profile_image_extensions_enabled")
        val responsiveWebGraphqlSkipUserProfileImageExtensionsEnabled: Boolean = false,
        @SerialName("responsive_web_graphql_timeline_navigation_enabled")
        val responsiveWebGraphqlTimelineNavigationEnabled: Boolean = true,
    )

    @Serializable
    data class Variables(
        @SerialName("listId")
        val listID: String,
        @SerialName("userId")
        val userID: String,
    )
}

@Serializable
internal data class RemoveMemberRequest(
    val variables: Variables,
    val features: Features = Features(),
    @SerialName("queryId")
    val queryID: String = "mm-P7pnWIiSXqbhWy4lUqQ",
) {
    @Serializable
    data class Features(
        @SerialName("profile_label_improvements_pcf_label_in_post_enabled")
        val profileLabelImprovementsPcfLabelInPostEnabled: Boolean = true,
        @SerialName("rweb_tipjar_consumption_enabled")
        val rwebTipjarConsumptionEnabled: Boolean = true,
        @SerialName("responsive_web_graphql_exclude_directive_enabled")
        val responsiveWebGraphqlExcludeDirectiveEnabled: Boolean = true,
        @SerialName("verified_phone_label_enabled")
        val verifiedPhoneLabelEnabled: Boolean = false,
        @SerialName("responsive_web_graphql_skip_user_profile_image_extensions_enabled")
        val responsiveWebGraphqlSkipUserProfileImageExtensionsEnabled: Boolean = false,
        @SerialName("responsive_web_graphql_timeline_navigation_enabled")
        val responsiveWebGraphqlTimelineNavigationEnabled: Boolean = true,
    )

    @Serializable
    data class Variables(
        @SerialName("listId")
        val listID: String,
        @SerialName("userId")
        val userID: String,
    )
}

@Serializable
internal data class EditListBannerRequest(
    val variables: Variables,
    val features: Features = Features(),
    @SerialName("queryId")
    val queryID: String = "vr7nuEH4eh7I_-In17FZCg",
) {
    @Serializable
    data class Features(
        @SerialName("profile_label_improvements_pcf_label_in_post_enabled")
        val profileLabelImprovementsPcfLabelInPostEnabled: Boolean = true,
        @SerialName("rweb_tipjar_consumption_enabled")
        val rwebTipjarConsumptionEnabled: Boolean = true,
        @SerialName("responsive_web_graphql_exclude_directive_enabled")
        val responsiveWebGraphqlExcludeDirectiveEnabled: Boolean = true,
        @SerialName("verified_phone_label_enabled")
        val verifiedPhoneLabelEnabled: Boolean = false,
        @SerialName("responsive_web_graphql_skip_user_profile_image_extensions_enabled")
        val responsiveWebGraphqlSkipUserProfileImageExtensionsEnabled: Boolean = false,
        @SerialName("responsive_web_graphql_timeline_navigation_enabled")
        val responsiveWebGraphqlTimelineNavigationEnabled: Boolean = true,
    )

    @Serializable
    data class Variables(
        @SerialName("listId")
        val listID: String,
        @SerialName("mediaId")
        val mediaID: String,
    )
}

@Serializable
internal data class CreateListRequest(
    val variables: Variables,
    val features: Features = Features(),
    @SerialName("queryId")
    val queryID: String = "vr7nuEH4eh7I_-In17FZCg",
) {
    @Serializable
    data class Features(
        @SerialName("profile_label_improvements_pcf_label_in_post_enabled")
        val profileLabelImprovementsPcfLabelInPostEnabled: Boolean = true,
        @SerialName("rweb_tipjar_consumption_enabled")
        val rwebTipjarConsumptionEnabled: Boolean = true,
        @SerialName("responsive_web_graphql_exclude_directive_enabled")
        val responsiveWebGraphqlExcludeDirectiveEnabled: Boolean = true,
        @SerialName("verified_phone_label_enabled")
        val verifiedPhoneLabelEnabled: Boolean = false,
        @SerialName("responsive_web_graphql_skip_user_profile_image_extensions_enabled")
        val responsiveWebGraphqlSkipUserProfileImageExtensionsEnabled: Boolean = false,
        @SerialName("responsive_web_graphql_timeline_navigation_enabled")
        val responsiveWebGraphqlTimelineNavigationEnabled: Boolean = true,
    )

    @Serializable
    data class Variables(
        val isPrivate: Boolean,
        val name: String,
        val description: String,
    )
}

@Serializable
@SerialName("TimelineTwitterList")
internal data class TimelineTwitterList(
//    @Contextual @SerialName(value = "itemType")
//    val itemType: ContentItemType? = null,
    @SerialName("displayType")
    val displayType: String? = null,
    @SerialName("list")
    val list: TwitterList? = null,
) : ItemContentUnion

@Serializable
internal data class TwitterList(
    @SerialName("created_at")
    val createdAt: Long? = null,
    @SerialName("default_banner_media")
    val defaultBannerMedia: DefaultBannerMedia? = null,
    @SerialName("default_banner_media_results")
    val defaultBannerMediaResults: DefaultBannerMediaResults? = null,
    @SerialName("custom_banner_media")
    val customBannerMedia: DefaultBannerMedia? = null,
    @SerialName("custom_banner_media_results")
    val customBannerMediaResults: DefaultBannerMediaResults? = null,
    val description: String? = null,
    @SerialName("facepile_urls")
    val facepileUrls: List<String>? = null,
    val following: Boolean? = null,
    val id: String? = null,
    @SerialName("id_str")
    val idStr: String? = null,
    @SerialName("is_member")
    val isMember: Boolean? = null,
    @SerialName("member_count")
    val memberCount: Long? = null,
    @SerialName("members_context")
    val membersContext: String? = null,
    val mode: String? = null,
    val muting: Boolean? = null,
    val name: String? = null,
    val pinning: Boolean? = null,
    @SerialName("subscriber_count")
    val subscriberCount: Long? = null,
    @SerialName("user_results")
    val userResults: UserResults? = null,
)

@Serializable
internal data class DefaultBannerMedia(
    @SerialName("media_info")
    val mediaInfo: MediaInfo? = null,
)

@Serializable
internal data class MediaInfo(
    @SerialName("original_img_url")
    val originalImgURL: String? = null,
    @SerialName("original_img_width")
    val originalImgWidth: Long? = null,
    @SerialName("original_img_height")
    val originalImgHeight: Long? = null,
    @SerialName("salient_rect")
    val salientRect: SalientRect? = null,
    @SerialName("__typename")
    val typename: String? = null,
)

@Serializable
internal data class SalientRect(
    val left: Long? = null,
    val top: Long? = null,
    val width: Long? = null,
    val height: Long? = null,
)

@Serializable
internal data class DefaultBannerMediaResults(
    val result: DefaultBannerMediaResultsResult? = null,
)

@Serializable
internal data class DefaultBannerMediaResultsResult(
    val id: String? = null,
    @SerialName("media_key")
    val mediaKey: String? = null,
    @SerialName("media_id")
    val mediaID: String? = null,
    @SerialName("media_info")
    val mediaInfo: MediaInfo? = null,
    @SerialName("__typename")
    val typename: String? = null,
)
