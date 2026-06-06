package dev.dimension.flare.data.network.pixiv

import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.pixiv.api.PixivAppResources
import dev.dimension.flare.data.network.pixiv.api.PixivAuthResources
import dev.dimension.flare.data.network.pixiv.api.createPixivAppResources
import dev.dimension.flare.data.network.pixiv.api.createPixivAuthResources
import dev.dimension.flare.data.network.pixiv.model.PixivBookmarkDetailResponse
import dev.dimension.flare.data.network.pixiv.model.PixivIllustDetailResponse
import dev.dimension.flare.data.network.pixiv.model.PixivIllustListResponse
import dev.dimension.flare.data.network.pixiv.model.PixivNullResponse
import dev.dimension.flare.data.network.pixiv.model.PixivTokenResponse
import dev.dimension.flare.data.network.pixiv.model.PixivTrendingTagsResponse
import dev.dimension.flare.data.network.pixiv.model.PixivUgoiraMetadataResponse
import dev.dimension.flare.data.network.pixiv.model.PixivUserDetailResponse
import dev.dimension.flare.data.network.pixiv.model.PixivUserListResponse
import dev.dimension.flare.data.platform.PixivCredential
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock

private val PixivHeaderPlugin =
    createClientPlugin("PixivHeaderPlugin") {
        onRequest { request, _ ->
            request.headers.append(HttpHeaders.UserAgent, PIXIV_ANDROID_USER_AGENT)
        }
    }

private fun pixivKtorfit(
    baseUrl: String,
    client: HttpClient,
) = de.jensklingenberg.ktorfit.ktorfit {
    baseUrl(baseUrl)
    httpClient(client)
    converterFactories(ResponseConverterFactory())
}

private fun pixivHttpClient(configure: HttpClientConfigBuilder = {}) =
    ktorClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(JSON)
        }
        configure()
        install(PixivHeaderPlugin)
    }

private typealias HttpClientConfigBuilder = io.ktor.client.HttpClientConfig<*>.() -> Unit

private data class PixivServiceClients(
    val authResources: PixivAuthResources,
    val appResources: PixivAppResources,
    val appClient: HttpClient,
)

private fun createPixivServiceClients(configureAppClient: HttpClientConfigBuilder = {}): PixivServiceClients {
    val authClient = pixivHttpClient()
    val appClient = pixivHttpClient(configureAppClient)
    return PixivServiceClients(
        authResources = pixivKtorfit(AUTH_BASE_URL, authClient).createPixivAuthResources(),
        appResources = pixivKtorfit(APP_API_BASE_URL, appClient).createPixivAppResources(),
        appClient = appClient,
    )
}

internal class PixivService private constructor(
    private val authResources: PixivAuthResources,
    private val appResources: PixivAppResources,
    private val appClient: HttpClient,
) {
    constructor() : this(createPixivServiceClients())

    constructor(
        accountKey: MicroBlogKey,
        credentialFlow: Flow<PixivCredential>,
        onCredentialRefreshed: suspend (PixivCredential) -> Unit,
    ) : this(
        createPixivServiceClients {
            install(PixivAuthPlugin) {
                this.accountKey = accountKey
                this.credentialFlow = credentialFlow
                this.onCredentialRefreshed = onCredentialRefreshed
                this.refreshToken = { clientId, clientSecret, refreshToken ->
                    pixivKtorfit(AUTH_BASE_URL, pixivHttpClient())
                        .createPixivAuthResources()
                        .refreshToken(
                            clientId = clientId,
                            clientSecret = clientSecret,
                            refreshToken = refreshToken,
                        ).toCredentialFallback(
                            credentialFlow = credentialFlow,
                        )
                }
            }
        },
    )

    private constructor(clients: PixivServiceClients) : this(
        authResources = clients.authResources,
        appResources = clients.appResources,
        appClient = clients.appClient,
    )

    suspend fun login(
        clientId: String,
        clientSecret: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        includePolicy: Boolean = true,
    ): PixivTokenResponse =
        authResources.login(
            clientId = clientId,
            clientSecret = clientSecret,
            code = code,
            codeVerifier = codeVerifier,
            redirectUri = redirectUri,
            includePolicy = includePolicy,
        )

    suspend fun refreshToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String,
        includePolicy: Boolean = true,
    ): PixivTokenResponse =
        authResources.refreshToken(
            clientId = clientId,
            clientSecret = clientSecret,
            refreshToken = refreshToken,
            includePolicy = includePolicy,
        )

    suspend fun recommendedIllusts(includeRankingIllusts: Boolean = true): PixivIllustListResponse =
        appResources.recommendedIllusts(
            includeRankingIllusts = includeRankingIllusts,
        )

    suspend fun recommendedManga(): PixivIllustListResponse = appResources.recommendedManga()

    suspend fun followedIllusts(restrict: PixivRestrict = PixivRestrict.Public): PixivIllustListResponse =
        appResources.followedIllusts(
            restrict = restrict.value,
        )

    suspend fun rankingIllusts(
        mode: PixivRankingMode,
        date: String? = null,
    ): PixivIllustListResponse =
        appResources.rankingIllusts(
            mode = mode.value,
            date = date,
        )

    suspend fun searchIllusts(
        word: String,
        sort: PixivSearchSort = PixivSearchSort.DateDesc,
        searchTarget: PixivIllustSearchTarget = PixivIllustSearchTarget.PartialMatchForTags,
        startDate: String? = null,
        endDate: String? = null,
    ): PixivIllustListResponse =
        appResources.searchIllusts(
            word = word,
            sort = sort.value,
            searchTarget = searchTarget.value,
            startDate = startDate,
            endDate = endDate,
        )

    suspend fun popularPreviewIllusts(
        word: String,
        searchTarget: PixivIllustSearchTarget = PixivIllustSearchTarget.PartialMatchForTags,
        startDate: String? = null,
        endDate: String? = null,
    ): PixivIllustListResponse =
        appResources.popularPreviewIllusts(
            word = word,
            searchTarget = searchTarget.value,
            startDate = startDate,
            endDate = endDate,
        )

    suspend fun illustDetail(illustId: Long): PixivIllustDetailResponse =
        appResources.illustDetail(
            illustId = illustId,
        )

    suspend fun relatedIllusts(illustId: Long): PixivIllustListResponse =
        appResources.relatedIllusts(
            illustId = illustId,
        )

    suspend fun userIllusts(
        userId: Long,
        type: PixivWorkType = PixivWorkType.Illust,
    ): PixivIllustListResponse =
        appResources.userIllusts(
            userId = userId,
            type = type.value,
        )

    suspend fun userBookmarkedIllusts(
        userId: Long,
        restrict: PixivRestrict = PixivRestrict.Public,
        tag: String? = null,
    ): PixivIllustListResponse =
        appResources.userBookmarkedIllusts(
            userId = userId,
            restrict = restrict.value,
            tag = tag,
        )

    suspend fun userDetail(userId: Long): PixivUserDetailResponse =
        appResources.userDetail(
            userId = userId,
        )

    suspend fun searchUsers(word: String): PixivUserListResponse =
        appResources.searchUsers(
            word = word,
        )

    suspend fun recommendedUsers(): PixivUserListResponse = appResources.recommendedUsers()

    suspend fun trendingTags(type: PixivTrendingTagType = PixivTrendingTagType.Illust): PixivTrendingTagsResponse =
        appResources.trendingTags(
            type = type.value,
        )

    suspend fun ugoiraMetadata(illustId: Long): PixivUgoiraMetadataResponse =
        appResources.ugoiraMetadata(
            illustId = illustId,
        )

    suspend fun bookmarkDetail(illustId: Long): PixivBookmarkDetailResponse =
        appResources.bookmarkDetail(
            illustId = illustId,
        )

    suspend fun addBookmark(
        illustId: Long,
        restrict: PixivRestrict = PixivRestrict.Public,
        tags: List<String> = emptyList(),
    ): PixivNullResponse =
        appResources.addBookmark(
            illustId = illustId,
            restrict = restrict.value,
            tags = tags,
        )

    suspend fun deleteBookmark(illustId: Long): PixivNullResponse =
        appResources.deleteBookmark(
            illustId = illustId,
        )

    suspend fun followUser(
        userId: Long,
        restrict: PixivRestrict = PixivRestrict.Public,
    ): PixivNullResponse =
        appResources.followUser(
            userId = userId,
            restrict = restrict.value,
        )

    suspend fun unfollowUser(userId: Long): PixivNullResponse =
        appResources.unfollowUser(
            userId = userId,
        )

    suspend fun nextIllusts(nextUrl: String): PixivIllustListResponse = appClient.get(nextUrl).body()

    suspend fun nextUsers(nextUrl: String): PixivUserListResponse = appClient.get(nextUrl).body()
}

private suspend fun PixivTokenResponse.toCredentialFallback(credentialFlow: Flow<PixivCredential>): PixivCredential? {
    val current = credentialFlow.firstOrNull() ?: return null
    return current.copy(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtEpochSeconds = Clock.System.now().epochSeconds + expiresIn,
        userId = user?.id ?: current.userId,
    )
}

internal enum class PixivRestrict(
    val value: String,
) {
    Public("public"),
    Private("private"),
}

internal enum class PixivRankingMode(
    val value: String,
) {
    Day("day"),
    Week("week"),
    Month("month"),
    DayMale("day_male"),
    DayFemale("day_female"),
    WeekOriginal("week_original"),
    WeekRookie("week_rookie"),
    DayManga("day_manga"),
}

internal enum class PixivSearchSort(
    val value: String,
) {
    DateDesc("date_desc"),
    DateAsc("date_asc"),
    PopularDesc("popular_desc"),
}

internal enum class PixivIllustSearchTarget(
    val value: String,
) {
    PartialMatchForTags("partial_match_for_tags"),
    ExactMatchForTags("exact_match_for_tags"),
    TitleAndCaption("title_and_caption"),
}

internal enum class PixivWorkType(
    val value: String,
) {
    Illust("illust"),
    Manga("manga"),
}

internal enum class PixivTrendingTagType(
    val value: String,
) {
    Illust("illust"),
    Novel("novel"),
}

internal const val PIXIV_IMAGE_REFERER = "https://www.pixiv.net/"

private const val APP_API_BASE_URL = "https://app-api.pixiv.net/"
private const val AUTH_BASE_URL = "https://oauth.secure.pixiv.net/"
private const val PIXIV_ANDROID_USER_AGENT = "PixivAndroidApp/6.128.0 (Android 13; Flare)"
