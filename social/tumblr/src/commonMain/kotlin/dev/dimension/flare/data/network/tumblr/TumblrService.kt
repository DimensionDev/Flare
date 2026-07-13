package dev.dimension.flare.data.network.tumblr

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.platform.TumblrCredential
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val TUMBLR_API_BASE_URL = "https://api.tumblr.com/v2/"
private const val TUMBLR_USER_AGENT = "Flare/1.0 (+https://github.com/DimensionDev/Flare)"
private const val DEFAULT_PAGE_SIZE = 20

private val TumblrHeaderPlugin =
    createClientPlugin("TumblrHeaderPlugin") {
        onRequest { request, _ ->
            request.headers.append(HttpHeaders.UserAgent, TUMBLR_USER_AGENT)
        }
    }

internal class TumblrService(
    private val credentialFlow: Flow<TumblrCredential>? = null,
    private val onCredentialRefreshed: suspend (TumblrCredential) -> Unit = {},
    private val authResources: TumblrAuthResources = tumblrKtorfit().createTumblrAuthResources(),
    private val resources: TumblrResources = tumblrKtorfit().createTumblrResources(),
) {
    suspend fun requestToken(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        code: String,
    ): TumblrTokenResponse =
        authResources.requestToken(
            grantType = "authorization_code",
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri,
            code = code,
        )

    suspend fun refreshToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String,
    ): TumblrTokenResponse =
        authResources.refreshToken(
            grantType = "refresh_token",
            clientId = clientId,
            clientSecret = clientSecret,
            refreshToken = refreshToken,
        )

    suspend fun userInfo(): TumblrUserInfoResponse = resources.userInfo(authorization()).requiredResponse()

    suspend fun dashboard(
        limit: Int,
        offset: Int?,
    ): TumblrPostsPage =
        resources
            .dashboard(
                authorization = authorization(),
                limit = limit.coercePageSize(),
                offset = offset,
            ).requiredResponse()

    suspend fun blogPosts(
        blogIdentifier: String,
        limit: Int,
        offset: Int?,
    ): TumblrPostsPage =
        resources
            .blogPosts(
                authorization = authorization(),
                blogIdentifier = blogIdentifier,
                limit = limit.coercePageSize(),
                offset = offset,
            ).requiredResponse()

    suspend fun post(
        blogIdentifier: String,
        postId: String,
    ): TumblrPost? =
        resources
            .blogPosts(
                authorization = authorization(),
                blogIdentifier = blogIdentifier,
                limit = 1,
                postId = postId,
            ).requiredResponse()
            .posts
            .firstOrNull()

    suspend fun tagged(
        tag: String,
        limit: Int,
        beforeTimestampSeconds: Long?,
    ): List<TumblrPost> =
        resources
            .tagged(
                authorization = authorization(),
                tag = tag.removePrefix("#"),
                limit = limit.coercePageSize(),
                beforeTimestampSeconds = beforeTimestampSeconds,
            ).requiredResponse()

    suspend fun blogInfo(blogIdentifier: String): TumblrBlog =
        resources
            .blogInfo(
                authorization = authorization(),
                blogIdentifier = blogIdentifier,
            ).requiredResponse()
            .blog

    suspend fun following(
        blogIdentifier: String,
        limit: Int,
        offset: Int?,
    ): TumblrBlogPage =
        resources
            .following(
                authorization = authorization(),
                blogIdentifier = blogIdentifier,
                limit = limit.coercePageSize(),
                offset = offset,
            ).requiredResponse()

    suspend fun followers(
        blogIdentifier: String,
        limit: Int,
        offset: Int?,
    ): TumblrFollowerPage =
        resources
            .followers(
                authorization = authorization(),
                blogIdentifier = blogIdentifier,
                limit = limit.coercePageSize(),
                offset = offset,
            ).requiredResponse()

    suspend fun like(
        postId: String,
        reblogKey: String,
    ) {
        resources.like(
            authorization = authorization(),
            postId = postId,
            reblogKey = reblogKey,
        )
    }

    suspend fun unlike(
        postId: String,
        reblogKey: String,
    ) {
        resources.unlike(
            authorization = authorization(),
            postId = postId,
            reblogKey = reblogKey,
        )
    }

    suspend fun reblog(
        blogIdentifier: String,
        postId: String,
        reblogKey: String,
        comment: String? = null,
        state: String? = null,
    ) {
        resources.reblog(
            authorization = authorization(),
            blogIdentifier = blogIdentifier,
            postId = postId,
            reblogKey = reblogKey,
            comment = comment,
            state = state,
        )
    }

    suspend fun follow(blogUrl: String) {
        resources.follow(
            authorization = authorization(),
            blogUrl = blogUrl,
        )
    }

    suspend fun unfollow(blogUrl: String) {
        resources.unfollow(
            authorization = authorization(),
            blogUrl = blogUrl,
        )
    }

    suspend fun deletePost(
        blogIdentifier: String,
        postId: String,
    ) {
        resources.deletePost(
            authorization = authorization(),
            blogIdentifier = blogIdentifier,
            postId = postId,
        )
    }

    suspend fun createPost(
        blogIdentifier: String,
        request: TumblrCreatePostRequest,
        media: List<Pair<TumblrNpfMedia, ComposeMediaFile>>,
    ) {
        val authorization = authorization()
        if (media.isEmpty()) {
            resources.createPost(
                authorization = authorization,
                blogIdentifier = blogIdentifier,
                request = request,
            )
            return
        }

        resources.createPost(
            authorization = authorization,
            blogIdentifier = blogIdentifier,
            body =
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "json",
                            value = JSON.encodeToString(request),
                            headers =
                                Headers.build {
                                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                },
                        )
                        media.forEach { (npfMedia, file) ->
                            append(
                                key = npfMedia.identifier ?: "media",
                                value = file.bytes,
                                headers =
                                    Headers.build {
                                        append(
                                            HttpHeaders.ContentDisposition,
                                            "form-data; name=\"${npfMedia.identifier ?: "media"}\"; filename=\"${file.fileName}\"",
                                        )
                                        append(HttpHeaders.ContentType, file.mimeType)
                                    },
                            )
                        }
                    },
                ),
        )
    }

    private suspend fun authorization(): String = "Bearer ${validCredential().accessToken}"

    private suspend fun validCredential(): TumblrCredential {
        val current = credentialFlow?.first() ?: error("Tumblr credential is missing")
        val expiresAt = current.expiresAtEpochSeconds ?: return current
        val now = Clock.System.now().toEpochMilliseconds() / 1000
        if (expiresAt - now > 5.minutes.inWholeSeconds) {
            return current
        }
        val refreshToken = current.refreshToken?.takeIf { it.isNotBlank() } ?: return current
        val response =
            refreshToken(
                clientId = dev.dimension.flare.social.tumblr.TumblrBuildConfig.clientId,
                clientSecret = dev.dimension.flare.social.tumblr.TumblrBuildConfig.clientSecret,
                refreshToken = refreshToken,
            )
        val updated =
            current.copy(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: current.refreshToken,
                tokenType = response.tokenType,
                scope = response.scope ?: current.scope,
                expiresAtEpochSeconds = response.expiresIn?.let { Clock.System.now().epochSeconds + it },
            )
        onCredentialRefreshed(updated)
        return updated
    }
}

private fun tumblrKtorfit() =
    ktorfit(TUMBLR_API_BASE_URL) {
        expectSuccess = true
        install(TumblrHeaderPlugin)
    }

private fun <T> TumblrEnvelope<T>.requiredResponse(): T = response ?: error("Tumblr response is missing")

internal data class ComposeMediaFile(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)

internal suspend fun FileItem.toTumblrComposeMediaFile(): ComposeMediaFile =
    ComposeMediaFile(
        bytes = readBytes(),
        fileName = name ?: "media",
        mimeType = mimeType ?: "application/octet-stream",
    )

private fun Int.coercePageSize(): Int = coerceIn(1, DEFAULT_PAGE_SIZE)
