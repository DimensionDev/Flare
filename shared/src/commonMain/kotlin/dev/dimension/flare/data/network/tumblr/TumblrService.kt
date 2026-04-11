package dev.dimension.flare.data.network.tumblr

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.tumblr.model.TumblrBlogInfoResponse
import dev.dimension.flare.data.network.tumblr.model.TumblrPostsResponse
import dev.dimension.flare.data.network.tumblr.model.TumblrResponse
import dev.dimension.flare.data.network.tumblr.model.TumblrUserInfoResponse
import dev.dimension.flare.data.network.tumblr.model.TumblrWriteResponse
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

internal class TumblrService(
    private val consumerKey: String,
    private val accessToken: String? = null,
) {
    private val client = ktorClient()

    suspend fun userInfo(): TumblrUserInfoResponse {
        val url = "https://api.tumblr.com/v2/user/info"
        return client
            .get(url) {
                accessToken?.let {
                    header(HttpHeaders.Authorization, "Bearer $it")
                }
            }.body<TumblrResponse<TumblrUserInfoResponse>>()
            .response
    }

    suspend fun dashboard(
        offset: Int,
        limit: Int,
    ): TumblrPostsResponse {
        val url =
            URLBuilder("https://api.tumblr.com/")
                .apply {
                    appendPathSegments("v2", "user", "dashboard")
                    parameters.append("offset", offset.toString())
                    parameters.append("limit", limit.coerceIn(1, 20).toString())
                    parameters.append("npf", "true")
                    parameters.append("reblog_info", "true")
                    parameters.append("notes_info", "true")
                }.buildString()
        return client
            .get(url) {
                accessToken?.let {
                    header(HttpHeaders.Authorization, "Bearer $it")
                }
            }.body<TumblrResponse<TumblrPostsResponse>>()
            .response
    }

    suspend fun blogInfo(blogIdentifier: String): TumblrBlogInfoResponse =
        client
            .get(
                URLBuilder("https://api.tumblr.com/")
                    .apply {
                        appendPathSegments("v2", "blog", blogIdentifier, "info")
                        parameters.append("api_key", consumerKey)
                    }.buildString(),
            ).body<TumblrResponse<TumblrBlogInfoResponse>>()
            .response

    suspend fun blogPosts(
        blogIdentifier: String,
        offset: Int,
        limit: Int,
        postId: String? = null,
    ): TumblrPostsResponse =
        client
            .get(
                URLBuilder("https://api.tumblr.com/")
                    .apply {
                        appendPathSegments("v2", "blog", blogIdentifier, "posts")
                        parameters.append("api_key", consumerKey)
                        postId?.let {
                            parameters.append("id", it)
                        } ?: run {
                            parameters.append("offset", offset.toString())
                            parameters.append("limit", limit.coerceIn(1, 20).toString())
                        }
                        parameters.append("npf", "true")
                        parameters.append("reblog_info", "true")
                        parameters.append("notes_info", "true")
                    }.buildString(),
            ).body<TumblrResponse<TumblrPostsResponse>>()
            .response

    suspend fun post(
        blogIdentifier: String,
        postId: String,
    ) = blogPosts(blogIdentifier = blogIdentifier, offset = 0, limit = 1, postId = postId)
        .posts
        .firstOrNull { it.idString == postId || it.id.toString() == postId }
        ?: error("Tumblr post not found: $blogIdentifier/$postId")

    suspend fun createTextPost(
        blogIdentifier: String,
        content: String,
    ): TumblrWriteResponse =
        authenticatedSubmitForm(
            URLBuilder("https://api.tumblr.com/")
                .apply {
                    appendPathSegments("v2", "blog", blogIdentifier, "post")
                }.buildString(),
            Parameters.build {
                append("type", "text")
                append("body", content)
            },
        )

    suspend fun reblogPost(
        blogIdentifier: String,
        postId: String,
        reblogKey: String,
        comment: String? = null,
    ): TumblrWriteResponse =
        authenticatedSubmitForm(
            URLBuilder("https://api.tumblr.com/")
                .apply {
                    appendPathSegments("v2", "blog", blogIdentifier, "post", "reblog")
                }.buildString(),
            Parameters.build {
                append("id", postId)
                append("reblog_key", reblogKey)
                comment?.takeIf { it.isNotBlank() }?.let {
                    append("comment", it)
                }
            },
        )

    suspend fun deletePost(
        blogIdentifier: String,
        postId: String,
    ): TumblrWriteResponse =
        authenticatedSubmitForm(
            URLBuilder("https://api.tumblr.com/")
                .apply {
                    appendPathSegments("v2", "blog", blogIdentifier, "post", "delete")
                }.buildString(),
            Parameters.build {
                append("id", postId)
            },
        )

    suspend fun like(
        postId: String,
        reblogKey: String,
    ): TumblrWriteResponse =
        authenticatedSubmitForm(
            URLBuilder("https://api.tumblr.com/")
                .apply {
                    appendPathSegments("v2", "user", "like")
                }.buildString(),
            Parameters.build {
                append("id", postId)
                append("reblog_key", reblogKey)
            },
        )

    suspend fun unlike(
        postId: String,
        reblogKey: String,
    ): TumblrWriteResponse =
        authenticatedSubmitForm(
            URLBuilder("https://api.tumblr.com/")
                .apply {
                    appendPathSegments("v2", "user", "unlike")
                }.buildString(),
            Parameters.build {
                append("id", postId)
                append("reblog_key", reblogKey)
            },
        )

    private suspend fun authenticatedSubmitForm(
        url: String,
        parameters: Parameters,
    ): TumblrWriteResponse =
        client
            .submitForm(
                url = url,
                formParameters = parameters,
            ) {
                require(!accessToken.isNullOrBlank()) {
                    "Tumblr authenticated endpoint requires an OAuth2 access token"
                }
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }.body<TumblrResponse<TumblrWriteResponse>>()
            .response
}
