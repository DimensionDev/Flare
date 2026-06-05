package dev.dimension.flare.data.network.pixiv

import dev.dimension.flare.data.platform.PixivCredential
import dev.dimension.flare.data.platform.PIXIV_HOST
import dev.dimension.flare.data.network.pixiv.model.PixivTokenResponse
import dev.dimension.flare.model.MicroBlogKey
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.time.Clock

internal data class PixivAuthorizationRequest(
    val authorizeRequestUrl: String,
    val codeVerifier: String,
    val state: String,
    val redirectUri: String,
)

internal suspend fun buildPixivAuthorizationRequest(redirectUri: String): PixivAuthorizationRequest {
    val codeVerifier = randomBase64Url(64)
    val codeChallenge = codeVerifier.toS256Challenge()
    val state = randomBase64Url(32)
    val url =
        URLBuilder("https://app-api.pixiv.net")
            .apply {
                appendPathSegments("web", "v1", "login")
                parameters.append("code_challenge", codeChallenge)
                parameters.append("code_challenge_method", "S256")
                parameters.append("client", "pixiv-android")
                parameters.append("state", state)
                parameters.append("redirect_uri", redirectUri)
            }.buildString()
    return PixivAuthorizationRequest(
        authorizeRequestUrl = url,
        codeVerifier = codeVerifier,
        state = state,
        redirectUri = redirectUri,
    )
}

internal fun parsePixivCallbackCode(
    callbackUrl: String,
    expectedState: String,
): String {
    val url = Url(callbackUrl)
    val state = url.parameters["state"]
    val code = url.parameters["code"]
    require(state == expectedState) {
        "State mismatch: expected $expectedState, got $state"
    }
    require(!code.isNullOrBlank()) {
        "No Pixiv authorization code"
    }
    return code
}

internal fun PixivTokenResponse.toCredential(
    clientId: String = PIXIV_ANDROID_CLIENT_ID,
    clientSecret: String = PIXIV_ANDROID_CLIENT_SECRET,
): PixivCredential =
    PixivCredential(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtEpochSeconds = Clock.System.now().epochSeconds + expiresIn,
        userId = user?.id ?: error("Pixiv token response does not include user id"),
        clientId = clientId,
        clientSecret = clientSecret,
    )

internal fun PixivCredential.accountKey(): MicroBlogKey = MicroBlogKey(userId.toString(), PIXIV_HOST)

private suspend fun String.toS256Challenge(): String {
    val hasher =
        CryptographyProvider
            .Default
            .get(SHA256)
            .hasher()
    return hasher.hash(encodeToByteArray()).encodeBase64Url()
}

private fun randomBase64Url(byteCount: Int): String =
    ByteArray(byteCount)
        .also { Random.Default.nextBytes(it) }
        .encodeBase64Url()

private fun ByteArray.encodeBase64Url(): String =
    Base64
        .encode(this)
        .trimEnd('=')
        .replace('+', '-')
        .replace('/', '_')

internal const val PIXIV_ANDROID_CLIENT_ID: String = "MOBrBDS8blbauoSck0ZfDbtuzpyT"
internal const val PIXIV_ANDROID_CLIENT_SECRET: String = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"
internal const val PIXIV_ANDROID_REDIRECT_URI: String = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"
