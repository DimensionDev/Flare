package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.AddMemberRequest
import dev.dimension.flare.data.network.xqt.model.CreateListRequest
import dev.dimension.flare.data.network.xqt.model.CreateListResponse
import dev.dimension.flare.data.network.xqt.model.EditListBannerRequest
import dev.dimension.flare.data.network.xqt.model.ListsManagementPageTimeline200Response
import dev.dimension.flare.data.network.xqt.model.ListsMembers200Response
import dev.dimension.flare.data.network.xqt.model.ListsOwnerships200Response
import dev.dimension.flare.data.network.xqt.model.RemoveListRequest
import dev.dimension.flare.data.network.xqt.model.RemoveListResponse
import dev.dimension.flare.data.network.xqt.model.RemoveMemberRequest
import dev.dimension.flare.data.network.xqt.model.UpdateListRequest

internal interface ListsApi {
    @GET("graphql/{pathQueryId}/ListsManagementPageTimeline")
    suspend fun getListsManagementPageTimeline(
        @Path("pathQueryId") pathQueryId: kotlin.String = "Ly8-jWQCO-MDBeYZM1Tigg",
        @Query("variables") variables: kotlin.String = "{\"count\": 100}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"profile_label_improvements_pcf_label_in_post_enabled\":true,\"rweb_tipjar_consumption_enabled\":true,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":false,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"premium_content_api_read_enabled\":false,\"communities_web_enable_tweet_community_results_fetch\":true,\"c9s_tweet_anatomy_moderator_badge_enabled\":true,\"responsive_web_grok_analyze_button_fetch_trends_enabled\":true,\"responsive_web_grok_analyze_post_followups_enabled\":false,\"responsive_web_jetfuel_frame\":false,\"responsive_web_grok_share_attachment_enabled\":true,\"articles_preview_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":true,\"tweet_awards_web_tipping_enabled\":false,\"responsive_web_grok_analysis_button_from_backend\":true,\"creator_subscriptions_quote_tweet_preview_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"rweb_video_timestamps_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_grok_image_annotation_enabled\":true,\"responsive_web_enhance_cards_enabled\":false}",
    ): Response<ListsManagementPageTimeline200Response>

    @POST("graphql/{pathQueryId}/CreateList")
    suspend fun createList(
        @Path("pathQueryId") pathQueryId: kotlin.String = "vr7nuEH4eh7I_-In17FZCg",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: CreateListRequest,
    ): Response<CreateListResponse>

    @POST("graphql/{pathQueryId}/EditListBanner")
    suspend fun editListBanner(
        @Path("pathQueryId") pathQueryId: kotlin.String = "YuAYKtb4nACpawz8OdBwCA",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: EditListBannerRequest,
    ): Response<CreateListResponse>

    @POST("graphql/{pathQueryId}/ListRemoveMember")
    suspend fun removeMember(
        @Path("pathQueryId") pathQueryId: kotlin.String = "mm-P7pnWIiSXqbhWy4lUqQ",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: RemoveMemberRequest,
    ): Response<CreateListResponse>

    @POST("graphql/{pathQueryId}/ListAddMember")
    suspend fun addMember(
        @Path("pathQueryId") pathQueryId: kotlin.String = "cfIJQu0q_i0WMDzQLa4dRA",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: AddMemberRequest,
    ): Response<CreateListResponse>

    @GET("graphql/{pathQueryId}/ListOwnerships")
    suspend fun getListOwnerships(
        @Path("pathQueryId") pathQueryId: kotlin.String = "qVUHFKgADgW-BrulG9OyqA",
        @Query("variables") variables: kotlin.String = "{\"userId\":\"123\",\"isListMemberTargetUserId\":\"456\",\"count\":20}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"profile_label_improvements_pcf_label_in_post_enabled\":true,\"rweb_tipjar_consumption_enabled\":true,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":false,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"premium_content_api_read_enabled\":false,\"communities_web_enable_tweet_community_results_fetch\":true,\"c9s_tweet_anatomy_moderator_badge_enabled\":true,\"responsive_web_grok_analyze_button_fetch_trends_enabled\":true,\"responsive_web_grok_analyze_post_followups_enabled\":false,\"responsive_web_jetfuel_frame\":false,\"responsive_web_grok_share_attachment_enabled\":true,\"articles_preview_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":true,\"tweet_awards_web_tipping_enabled\":false,\"responsive_web_grok_analysis_button_from_backend\":true,\"creator_subscriptions_quote_tweet_preview_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"rweb_video_timestamps_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_grok_image_annotation_enabled\":true,\"responsive_web_enhance_cards_enabled\":false}",
    ): Response<ListsOwnerships200Response>

    @GET("graphql/{pathQueryId}/ListMembers")
    suspend fun getListMembers(
        @Path("pathQueryId") pathQueryId: kotlin.String = "tWmAZLQ9yIIX1bg4wfv8Hg",
        @Query("variables") variables: kotlin.String = "{\"listId\":\"123\",\"count\":20}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"profile_label_improvements_pcf_label_in_post_enabled\":true,\"rweb_tipjar_consumption_enabled\":true,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":false,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"premium_content_api_read_enabled\":false,\"communities_web_enable_tweet_community_results_fetch\":true,\"c9s_tweet_anatomy_moderator_badge_enabled\":true,\"responsive_web_grok_analyze_button_fetch_trends_enabled\":true,\"responsive_web_grok_analyze_post_followups_enabled\":false,\"responsive_web_jetfuel_frame\":false,\"responsive_web_grok_share_attachment_enabled\":true,\"articles_preview_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":true,\"tweet_awards_web_tipping_enabled\":false,\"responsive_web_grok_analysis_button_from_backend\":true,\"creator_subscriptions_quote_tweet_preview_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"rweb_video_timestamps_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_grok_image_annotation_enabled\":true,\"responsive_web_enhance_cards_enabled\":false}",
    ): Response<ListsMembers200Response>

    @POST("graphql/{pathQueryId}/DeleteList")
    suspend fun deleteList(
        @Path("pathQueryId") pathQueryId: kotlin.String = "UnN9Th1BDbeLjpgjGSpL3Q",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: RemoveListRequest,
    ): Response<RemoveListResponse>

    @POST("graphql/{pathQueryId}/UpdateList")
    suspend fun updateList(
        @Path("pathQueryId") pathQueryId: kotlin.String = "hC86V8CK9BF4EkA5Wcq9hQ",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: UpdateListRequest,
    ): Response<CreateListResponse>

    @GET("graphql/{pathQueryId}/ListByRestId")
    suspend fun getListByRestId(
        @Path("pathQueryId") pathQueryId: kotlin.String = "oygmAig8kjn0pKsx_bUadQ",
        @Query("variables") variables: kotlin.String = "{\"listId\":\"123\"}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"profile_label_improvements_pcf_label_in_post_enabled\":true,\"rweb_tipjar_consumption_enabled\":true,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":false,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"responsive_web_graphql_timeline_navigation_enabled\":true}",
    ): Response<CreateListResponse>
}
