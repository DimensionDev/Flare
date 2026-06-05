package dev.dimension.flare.data.network.pixiv.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PixivTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("token_type")
    val tokenType: String,
    val scope: String? = null,
    val user: PixivUser? = null,
)

@Serializable
internal data class PixivIllustListResponse(
    val illusts: List<PixivIllust> = emptyList(),
    @SerialName("next_url")
    val nextUrl: String? = null,
)

@Serializable
internal data class PixivIllustDetailResponse(
    val illust: PixivIllust,
)

@Serializable
internal data class PixivUserListResponse(
    @SerialName("user_previews")
    val userPreviews: List<PixivUserPreview> = emptyList(),
    @SerialName("next_url")
    val nextUrl: String? = null,
)

@Serializable
internal data class PixivUserDetailResponse(
    val user: PixivUser,
    val profile: PixivUserProfile? = null,
    @SerialName("profile_publicity")
    val profilePublicity: PixivProfilePublicity? = null,
    @SerialName("workspace")
    val workspace: PixivWorkspace? = null,
)

@Serializable
internal data class PixivTrendingTagsResponse(
    @SerialName("trend_tags")
    val trendTags: List<PixivTrendTag> = emptyList(),
)

@Serializable
internal data class PixivBookmarkDetailResponse(
    @SerialName("bookmark_detail")
    val bookmarkDetail: PixivBookmarkDetail,
)

@Serializable
internal data class PixivUgoiraMetadataResponse(
    @SerialName("ugoira_metadata")
    val ugoiraMetadata: PixivUgoiraMetadata,
)

@Serializable
internal data class PixivNullResponse(
    val error: Boolean? = null,
    val message: String? = null,
)

@Serializable
internal data class PixivIllust(
    val id: Long,
    val title: String,
    val type: String,
    @SerialName("image_urls")
    val imageUrls: PixivImageUrls,
    val caption: String = "",
    val restrict: Int = 0,
    val user: PixivUser,
    val tags: List<PixivTag> = emptyList(),
    val tools: List<String> = emptyList(),
    @SerialName("create_date")
    val createDate: String,
    @SerialName("page_count")
    val pageCount: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("sanity_level")
    val sanityLevel: Int = 0,
    @SerialName("x_restrict")
    val xRestrict: Int = 0,
    val series: PixivSeries? = null,
    @SerialName("meta_single_page")
    val metaSinglePage: PixivMetaSinglePage? = null,
    @SerialName("meta_pages")
    val metaPages: List<PixivMetaPage> = emptyList(),
    @SerialName("total_view")
    val totalView: Long = 0,
    @SerialName("total_bookmarks")
    val totalBookmarks: Long = 0,
    @SerialName("total_comments")
    val totalComments: Long = 0,
    @SerialName("is_bookmarked")
    val isBookmarked: Boolean = false,
    val visible: Boolean = true,
    @SerialName("is_muted")
    val isMuted: Boolean = false,
    @SerialName("illust_ai_type")
    val illustAiType: Int = 0,
)

@Serializable
internal data class PixivImageUrls(
    @SerialName("square_medium")
    val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val original: String? = null,
)

@Serializable
internal data class PixivMetaSinglePage(
    @SerialName("original_image_url")
    val originalImageUrl: String? = null,
)

@Serializable
internal data class PixivMetaPage(
    @SerialName("image_urls")
    val imageUrls: PixivImageUrls,
)

@Serializable
internal data class PixivTag(
    val name: String,
    @SerialName("translated_name")
    val translatedName: String? = null,
)

@Serializable
internal data class PixivSeries(
    val id: Long,
    val title: String,
)

@Serializable
internal data class PixivUser(
    val id: Long,
    val name: String,
    val account: String,
    @SerialName("profile_image_urls")
    val profileImageUrls: PixivProfileImageUrls? = null,
    val comment: String? = null,
    @SerialName("is_followed")
    val isFollowed: Boolean = false,
    @SerialName("is_premium")
    val isPremium: Boolean = false,
    @SerialName("x_restrict")
    val xRestrict: Int = 0,
)

@Serializable
internal data class PixivProfileImageUrls(
    @SerialName("px_16x16")
    val px16x16: String? = null,
    @SerialName("px_50x50")
    val px50x50: String? = null,
    @SerialName("px_170x170")
    val px170x170: String? = null,
    val medium: String? = null,
)

@Serializable
internal data class PixivUserPreview(
    val user: PixivUser,
    val illusts: List<PixivIllust> = emptyList(),
    val novels: List<PixivNovel> = emptyList(),
    @SerialName("is_muted")
    val isMuted: Boolean = false,
)

@Serializable
internal data class PixivUserProfile(
    val webpage: String? = null,
    val gender: String? = null,
    val birth: String? = null,
    @SerialName("birth_day")
    val birthDay: String? = null,
    @SerialName("birth_year")
    val birthYear: Int? = null,
    val region: String? = null,
    @SerialName("address_id")
    val addressId: Int? = null,
    @SerialName("country_code")
    val countryCode: String? = null,
    val job: String? = null,
    @SerialName("job_id")
    val jobId: Int? = null,
    @SerialName("total_follow_users")
    val totalFollowUsers: Long = 0,
    @SerialName("total_mypixiv_users")
    val totalMyPixivUsers: Long = 0,
    @SerialName("total_illusts")
    val totalIllusts: Long = 0,
    @SerialName("total_manga")
    val totalManga: Long = 0,
    @SerialName("total_novels")
    val totalNovels: Long = 0,
    @SerialName("total_illust_bookmarks_public")
    val totalIllustBookmarksPublic: Long = 0,
    @SerialName("background_image_url")
    val backgroundImageUrl: String? = null,
    @SerialName("twitter_account")
    val twitterAccount: String? = null,
    @SerialName("twitter_url")
    val twitterUrl: String? = null,
    @SerialName("pawoo_url")
    val pawooUrl: String? = null,
    @SerialName("is_premium")
    val isPremium: Boolean = false,
    @SerialName("is_using_custom_profile_image")
    val isUsingCustomProfileImage: Boolean = false,
)

@Serializable
internal data class PixivProfilePublicity(
    val gender: String? = null,
    val region: String? = null,
    @SerialName("birth_day")
    val birthDay: String? = null,
    @SerialName("birth_year")
    val birthYear: String? = null,
    val job: String? = null,
    val pawoo: Boolean? = null,
)

@Serializable
internal data class PixivWorkspace(
    val pc: String? = null,
    val monitor: String? = null,
    val tool: String? = null,
    val scanner: String? = null,
    val tablet: String? = null,
    val mouse: String? = null,
    val printer: String? = null,
    val desktop: String? = null,
    val music: String? = null,
    val desk: String? = null,
    val chair: String? = null,
    val comment: String? = null,
    @SerialName("workspace_image_url")
    val workspaceImageUrl: String? = null,
)

@Serializable
internal data class PixivTrendTag(
    val tag: String,
    @SerialName("translated_name")
    val translatedName: String? = null,
    val illust: PixivIllust,
)

@Serializable
internal data class PixivBookmarkDetail(
    @SerialName("is_bookmarked")
    val isBookmarked: Boolean,
    val tags: List<PixivBookmarkTag> = emptyList(),
    val restrict: String? = null,
)

@Serializable
internal data class PixivBookmarkTag(
    val name: String,
    @SerialName("is_registered")
    val isRegistered: Boolean = false,
)

@Serializable
internal data class PixivUgoiraMetadata(
    @SerialName("zip_urls")
    val zipUrls: PixivUgoiraZipUrls,
    val frames: List<PixivUgoiraFrame> = emptyList(),
)

@Serializable
internal data class PixivUgoiraZipUrls(
    val medium: String? = null,
)

@Serializable
internal data class PixivUgoiraFrame(
    val file: String,
    val delay: Int,
)

@Serializable
internal data class PixivNovel(
    val id: Long,
    val title: String,
)
