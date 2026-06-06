package dev.dimension.flare.data.network.pixiv.api

import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.pixiv.model.PixivBookmarkDetailResponse
import dev.dimension.flare.data.network.pixiv.model.PixivIllustDetailResponse
import dev.dimension.flare.data.network.pixiv.model.PixivIllustListResponse
import dev.dimension.flare.data.network.pixiv.model.PixivNullResponse
import dev.dimension.flare.data.network.pixiv.model.PixivTrendingTagsResponse
import dev.dimension.flare.data.network.pixiv.model.PixivUgoiraMetadataResponse
import dev.dimension.flare.data.network.pixiv.model.PixivUserDetailResponse
import dev.dimension.flare.data.network.pixiv.model.PixivUserListResponse

internal interface PixivAppResources {
    @GET("v1/illust/recommended")
    suspend fun recommendedIllusts(
        @Query("include_privacy_policy") includePrivacyPolicy: Boolean = true,
        @Query("filter") filter: String = "for_android",
        @Query("include_ranking_illusts") includeRankingIllusts: Boolean = true,
    ): PixivIllustListResponse

    @GET("v1/manga/recommended")
    suspend fun recommendedManga(
        @Query("include_privacy_policy") includePrivacyPolicy: Boolean = true,
        @Query("filter") filter: String = "for_android",
        @Query("include_ranking_illusts") includeRankingIllusts: Boolean = true,
    ): PixivIllustListResponse

    @GET("v2/illust/follow")
    suspend fun followedIllusts(
        @Query("restrict") restrict: String,
    ): PixivIllustListResponse

    @GET("v1/illust/ranking")
    suspend fun rankingIllusts(
        @Query("filter") filter: String = "for_android",
        @Query("mode") mode: String,
        @Query("date") date: String? = null,
    ): PixivIllustListResponse

    @GET("v1/search/illust")
    suspend fun searchIllusts(
        @Query("filter") filter: String = "for_android",
        @Query("include_translated_tag_results") includeTranslatedTagResults: Boolean = true,
        @Query("merge_plain_keyword_results") mergePlainKeywordResults: Boolean = true,
        @Query("word") word: String,
        @Query("sort") sort: String,
        @Query("search_target") searchTarget: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
    ): PixivIllustListResponse

    @GET("v1/search/popular-preview/illust")
    suspend fun popularPreviewIllusts(
        @Query("filter") filter: String = "for_android",
        @Query("include_translated_tag_results") includeTranslatedTagResults: Boolean = true,
        @Query("merge_plain_keyword_results") mergePlainKeywordResults: Boolean = true,
        @Query("word") word: String,
        @Query("search_target") searchTarget: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
    ): PixivIllustListResponse

    @GET("v1/illust/detail")
    suspend fun illustDetail(
        @Query("filter") filter: String = "for_android",
        @Query("illust_id") illustId: Long,
    ): PixivIllustDetailResponse

    @GET("v2/illust/related")
    suspend fun relatedIllusts(
        @Query("filter") filter: String = "for_android",
        @Query("illust_id") illustId: Long,
    ): PixivIllustListResponse

    @GET("v1/user/illusts")
    suspend fun userIllusts(
        @Query("filter") filter: String = "for_android",
        @Query("user_id") userId: Long,
        @Query("type") type: String,
    ): PixivIllustListResponse

    @GET("v1/user/bookmarks/illust")
    suspend fun userBookmarkedIllusts(
        @Query("user_id") userId: Long,
        @Query("restrict") restrict: String,
        @Query("tag") tag: String? = null,
    ): PixivIllustListResponse

    @GET("v1/user/detail")
    suspend fun userDetail(
        @Query("filter") filter: String = "for_android",
        @Query("user_id") userId: Long,
    ): PixivUserDetailResponse

    @GET("v1/search/user")
    suspend fun searchUsers(
        @Query("filter") filter: String = "for_android",
        @Query("word") word: String,
    ): PixivUserListResponse

    @GET("v1/user/recommended")
    suspend fun recommendedUsers(
        @Query("filter") filter: String = "for_android",
    ): PixivUserListResponse

    @GET("v1/trending-tags/{type}")
    suspend fun trendingTags(
        @Path("type") type: String,
        @Query("filter") filter: String = "for_android",
        @Query("include_translated_tag_results") includeTranslatedTagResults: Boolean = true,
    ): PixivTrendingTagsResponse

    @GET("v1/ugoira/metadata")
    suspend fun ugoiraMetadata(
        @Query("illust_id") illustId: Long,
    ): PixivUgoiraMetadataResponse

    @GET("v2/illust/bookmark/detail")
    suspend fun bookmarkDetail(
        @Query("illust_id") illustId: Long,
    ): PixivBookmarkDetailResponse

    @POST("v2/illust/bookmark/add")
    @FormUrlEncoded
    suspend fun addBookmark(
        @Field("illust_id") illustId: Long,
        @Field("restrict") restrict: String,
        @Field("tags[]") tags: List<String> = emptyList(),
    ): PixivNullResponse

    @POST("v1/illust/bookmark/delete")
    @FormUrlEncoded
    suspend fun deleteBookmark(
        @Field("illust_id") illustId: Long,
    ): PixivNullResponse

    @POST("v1/user/follow/add")
    @FormUrlEncoded
    suspend fun followUser(
        @Field("user_id") userId: Long,
        @Field("restrict") restrict: String,
    ): PixivNullResponse

    @POST("v1/user/follow/delete")
    @FormUrlEncoded
    suspend fun unfollowUser(
        @Field("user_id") userId: Long,
    ): PixivNullResponse
}
