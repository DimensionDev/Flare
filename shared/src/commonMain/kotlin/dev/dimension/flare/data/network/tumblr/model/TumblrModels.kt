package dev.dimension.flare.data.network.tumblr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class TumblrResponse<T>(
    val meta: TumblrMeta,
    val response: T,
)

@Serializable
internal data class TumblrMeta(
    val status: Int,
    val msg: String,
)

@Serializable
internal data class TumblrConsumerCredential(
    @SerialName("consumer_key")
    val consumerKey: String,
    @SerialName("consumer_secret")
    val consumerSecret: String,
)

@Serializable
internal data class TumblrApplicationCredential(
    @SerialName("consumer_key")
    val consumerKey: String,
    @SerialName("consumer_secret")
    val consumerSecret: String,
    @SerialName("auth_state")
    val authState: String? = null,
)

@Serializable
internal data class TumblrOAuth2TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long? = null,
    @SerialName("token_type")
    val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
)

@Serializable
internal data class TumblrUserInfoResponse(
    val user: TumblrUser,
)

@Serializable
internal data class TumblrUser(
    val name: String,
    val blogs: List<TumblrBlog> = emptyList(),
)

@Serializable
internal data class TumblrBlogInfoResponse(
    val blog: TumblrBlog,
)

@Serializable
internal data class TumblrPostsResponse(
    val posts: List<TumblrPost> = emptyList(),
    val totalPosts: Int? = null,
)

@Serializable
internal data class TumblrWriteResponse(
    val id: Long? = null,
    @SerialName("id_string")
    val idString: String? = null,
)

@Serializable
internal data class TumblrBlog(
    val name: String,
    val title: String? = null,
    val url: String,
    val description: String? = null,
    val posts: Long? = null,
    val followers: Long? = null,
    val primary: Boolean = false,
    @SerialName("can_post")
    val canPost: Boolean = false,
)

@Serializable
internal data class TumblrPost(
    val id: Long,
    @SerialName("id_string")
    val idString: String? = null,
    @SerialName("blog_name")
    val blogName: String,
    @SerialName("post_url")
    val postUrl: String? = null,
    val summary: String? = null,
    val timestamp: Long? = null,
    @SerialName("reblog_key")
    val reblogKey: String? = null,
    @SerialName("note_count")
    val noteCount: Long? = null,
    val liked: Boolean = false,
    @SerialName("can_like")
    val canLike: Boolean = false,
    @SerialName("can_reblog")
    val canReblog: Boolean = false,
    val tags: List<String> = emptyList(),
    val type: String? = null,
    val content: JsonArray? = null,
    val trail: JsonArray? = null,
    @SerialName("reblogged_root_id")
    val rebloggedRootId: JsonElement? = null,
)
