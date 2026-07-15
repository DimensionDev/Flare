package dev.dimension.flare.data.network.vvo

import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.nullableFallbackJson
import dev.dimension.flare.data.network.vvo.api.ConfigApi
import dev.dimension.flare.data.network.vvo.api.StatusApi
import dev.dimension.flare.data.network.vvo.api.TimelineApi
import dev.dimension.flare.data.network.vvo.api.UserApi
import dev.dimension.flare.data.network.vvo.api.createConfigApi
import dev.dimension.flare.data.network.vvo.api.createStatusApi
import dev.dimension.flare.data.network.vvo.api.createTimelineApi
import dev.dimension.flare.data.network.vvo.api.createUserApi
import dev.dimension.flare.data.network.vvo.model.Config
import dev.dimension.flare.data.network.vvo.model.EmojiData
import dev.dimension.flare.data.network.vvo.model.UploadResponse
import dev.dimension.flare.data.network.vvo.model.VVOResponse
import dev.dimension.flare.data.platform.VVoCredential
import dev.dimension.flare.model.vvoHost
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private val baseUrl = "https://$vvoHost/"
private val chocolateRefreshInterval = 1.days
private val defaultConfigLoginRetryDelay = 500.milliseconds
private const val CONFIG_LOGIN_RETRY_LIMIT = 5

private typealias VVOHttpClientFactory = (HttpClientConfig<*>.() -> Unit) -> HttpClient

private fun defaultVvoHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = ktorClient(config)

private fun vvoKtorfit(
    url: String = baseUrl,
    chocolateProvider: suspend () -> String?,
    httpClientFactory: VVOHttpClientFactory,
) = de.jensklingenberg.ktorfit.ktorfit {
    baseUrl(url)
    httpClient(
        httpClientFactory {
            install(ContentNegotiation) {
                nullableFallbackJson(JSON)
            }
            install(VVOHeaderPlugin) {
                this.chocolateProvider = chocolateProvider
            }
        },
    )
    converterFactories(
        VVOResponseConverterFactory(),
        ResponseConverterFactory(),
    )
}

private class VVOApis(
    chocolateProvider: suspend () -> String?,
    httpClientFactory: VVOHttpClientFactory,
) {
    private val ktorfit =
        vvoKtorfit(
            chocolateProvider = chocolateProvider,
            httpClientFactory = httpClientFactory,
        )
    val timelineApi: TimelineApi = ktorfit.createTimelineApi()
    val userApi: UserApi = ktorfit.createUserApi()
    val configApi: ConfigApi = ktorfit.createConfigApi()
    val statusApi: StatusApi = ktorfit.createStatusApi()
}

internal class VVOService private constructor(
    private val credentialFlow: Flow<VVoCredential>,
    private val refreshCookieWhenStale: Boolean,
    private val configLoginRetryDelay: Duration,
    private val onCredentialRefreshed: suspend (VVoCredential) -> Unit,
    private val httpClientFactory: VVOHttpClientFactory,
    private val apis: VVOApis,
) : TimelineApi by apis.timelineApi,
    UserApi by apis.userApi,
    ConfigApi by apis.configApi,
    StatusApi by apis.statusApi {
    constructor(
        credentialFlow: Flow<VVoCredential>,
        refreshCookieWhenStale: Boolean = false,
        configLoginRetryDelay: Duration = defaultConfigLoginRetryDelay,
        onCredentialRefreshed: suspend (VVoCredential) -> Unit = {},
        httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = ::defaultVvoHttpClient,
    ) : this(
        credentialFlow = credentialFlow,
        refreshCookieWhenStale = refreshCookieWhenStale,
        configLoginRetryDelay = configLoginRetryDelay,
        onCredentialRefreshed = onCredentialRefreshed,
        httpClientFactory = httpClientFactory,
        apis =
            VVOApis(
                chocolateProvider = { credentialFlow.firstOrNull()?.chocolate },
                httpClientFactory = httpClientFactory,
            ),
    )

    private val refreshMutex = Mutex()

    companion object {
        fun checkChocolates(chocolate: String): Boolean =
            chocolate
                .split(';')
                .mapNotNull { it.toCookiePairOrNull() }
                .toMap()
                .let {
                    it.containsKey("MLOGIN") && it["MLOGIN"] == "1"
                }
    }

    override suspend fun config(): VVOResponse<Config> {
        val refreshedCredential = refreshChocolateIfNeeded()
        var requestCredential = refreshedCredential ?: currentCredential()

        var response = configApi(refreshedCredential).config()
        if (response.data?.login == true) {
            return response
        }

        repeat(CONFIG_LOGIN_RETRY_LIMIT) {
            val recoveredCredential = refreshChocolate(credentialToRefresh = requestCredential) ?: return response
            if (recoveredCredential.chocolate.isBlank()) {
                return response
            }
            requestCredential = recoveredCredential
            delayConfigLoginRetry()
            response = configApi(recoveredCredential).config()
            if (response.data?.login == true) {
                return response
            }
        }

        return response
    }

    suspend fun getUid(screenName: String): String? {
        val response =
            httpClientFactory {
                followRedirects = false
                install(VVOHeaderPlugin) {
                    chocolateProvider = ::currentChocolate
                }
            }.get("https://$vvoHost/n/$screenName")
        return response.headers["Location"]?.let {
            return it.split('/').last()
        }
    }

    suspend fun uploadPic(
        st: String,
        filename: String,
        bytes: ByteArray,
        xsrfToken: String = st,
        type: String = "json",
    ): UploadResponse =
        httpClientFactory {
            install(HttpTimeout) {
                connectTimeoutMillis = 2.minutes.inWholeMilliseconds
                requestTimeoutMillis = 2.minutes.inWholeMilliseconds
                socketTimeoutMillis = 2.minutes.inWholeMilliseconds
            }
            install(VVOHeaderPlugin) {
                chocolateProvider = ::currentChocolate
            }
        }.submitFormWithBinaryData(
            url = "https://$vvoHost/api/statuses/uploadPic",
            formData =
                formData {
                    append("type", type)
                    append(
                        "pic",
                        filename,
                        bodyBuilder = {
                            writeFully(bytes)
                        },
                        size = bytes.size.toLong(),
                        contentType = ContentType.Image.JPEG,
                    )

                    append("st", st)
                },
            block = {
                header("X-Xsrf-Token", xsrfToken)
            },
        ).bodyAsText()
            .decodeJson<UploadResponse>()

    suspend fun emojis(): EmojiData =
        httpClientFactory {
            install(ContentNegotiation) {
                nullableFallbackJson(JSON)
            }
        }.get("https://flareapp.moe/emoji.json")
            .body()

    private suspend fun delayConfigLoginRetry() {
        if (configLoginRetryDelay > Duration.ZERO) {
            delay(configLoginRetryDelay)
        }
    }

    private fun configApi(credential: VVoCredential?): ConfigApi =
        credential
            ?.let {
                VVOApis(
                    chocolateProvider = { it.chocolate },
                    httpClientFactory = httpClientFactory,
                ).configApi
            }
            ?: apis.configApi

    private suspend fun refreshChocolateIfNeeded(): VVoCredential? {
        if (!refreshCookieWhenStale) {
            return null
        }
        val lastRefreshEpochMillis = currentCredential()?.lastCookieRefreshEpochMillis
        val now = Clock.System.now().toEpochMilliseconds()
        return if (shouldRefreshVvoCookie(lastRefreshEpochMillis, now)) {
            runCatching {
                refreshChocolate(
                    refreshedAtEpochMillis = now,
                    refreshOnlyWhenStale = true,
                )
            }.getOrNull()
        } else {
            null
        }
    }

    private suspend fun refreshChocolate(
        refreshedAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
        refreshOnlyWhenStale: Boolean = false,
        credentialToRefresh: VVoCredential? = null,
    ): VVoCredential? {
        return refreshMutex.withLock {
            val latestCredential = currentCredential()
            val currentCredential =
                credentialToRefresh
                    ?.let { requestedCredential ->
                        if (latestCredential != null && latestCredential.isRefreshedAfter(requestedCredential)) {
                            return@withLock latestCredential
                        }
                        requestedCredential
                    }
                    ?: latestCredential
                    ?: return@withLock null
            if (refreshOnlyWhenStale &&
                !shouldRefreshVvoCookie(
                    lastRefreshEpochMillis = currentCredential.lastCookieRefreshEpochMillis,
                    nowEpochMillis = refreshedAtEpochMillis,
                )
            ) {
                return@withLock currentCredential
            }
            val currentChocolate =
                currentCredential
                    .chocolate
                    .takeIf { it.isNotBlank() }
                    ?: return@withLock null

            val response =
                httpClientFactory {
                    followRedirects = false
                    install(VVOHeaderPlugin) {
                        chocolateProvider = { currentChocolate }
                    }
                }.get("https://$vvoHost/")

            val refreshedChocolate =
                mergeVvoCookieHeader(
                    currentCookieHeader = currentChocolate,
                    setCookieHeaders = response.headers.getAll(HttpHeaders.SetCookie).orEmpty(),
                ) ?: currentChocolate

            currentCredential
                .copy(
                    chocolate = refreshedChocolate,
                    lastCookieRefreshEpochMillis = refreshedAtEpochMillis,
                ).also {
                    onCredentialRefreshed(it)
                }
        }
    }

    private suspend fun currentCredential(): VVoCredential? = credentialFlow.firstOrNull()

    private suspend fun currentChocolate(): String? = currentCredential()?.chocolate
}

private fun VVoCredential.isRefreshedAfter(other: VVoCredential): Boolean {
    val lastRefresh = lastCookieRefreshEpochMillis ?: return false
    val otherLastRefresh = other.lastCookieRefreshEpochMillis
    return this != other && (otherLastRefresh == null || lastRefresh > otherLastRefresh)
}

internal fun shouldRefreshVvoCookie(
    lastRefreshEpochMillis: Long?,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): Boolean =
    lastRefreshEpochMillis == null ||
        nowEpochMillis - lastRefreshEpochMillis > chocolateRefreshInterval.inWholeMilliseconds

internal fun mergeVvoCookieHeader(
    currentCookieHeader: String,
    setCookieHeaders: List<String>,
): String? {
    if (setCookieHeaders.isEmpty()) {
        return null
    }

    val cookies = linkedMapOf<String, String>()
    currentCookieHeader
        .split(';')
        .mapNotNull { it.toCookiePairOrNull() }
        .forEach { (key, value) ->
            cookies[key] = value
        }

    var changed = false
    setCookieHeaders
        .mapNotNull { it.toSetCookiePairOrNull() }
        .forEach { (key, value) ->
            val previous = cookies.put(key, value)
            changed = previous != value || changed
        }

    if (!changed) {
        return null
    }
    return cookies.entries.joinToString("; ") { (key, value) -> "$key=$value" }
}

private fun String.toCookiePairOrNull(): Pair<String, String>? {
    val separator = indexOf('=')
    if (separator <= 0) {
        return null
    }
    val key = substring(0, separator).trim()
    val value = substring(separator + 1).trim()
    return if (key.isBlank()) {
        null
    } else {
        key to value
    }
}

private fun String.toSetCookiePairOrNull(): Pair<String, String>? {
    val cookie = substringBefore(';').toCookiePairOrNull() ?: return null
    return cookie.takeUnless { isExpiredSetCookie() }
}

private fun String.isExpiredSetCookie(): Boolean =
    split(';')
        .drop(1)
        .map { it.trim().lowercase() }
        .any { attribute ->
            when {
                attribute.startsWith("max-age=") -> {
                    attribute.substringAfter("=").toLongOrNull()?.let { it <= 0 } == true
                }

                attribute.startsWith("expires=") -> {
                    parseCookieExpiresEpochSeconds(attribute.substringAfter("="))
                        ?.let { it <= Clock.System.now().epochSeconds } == true
                }

                else -> {
                    false
                }
            }
        }

private fun parseCookieExpiresEpochSeconds(value: String): Long? {
    val match =
        CookieExpiresRegex
            .find(value.trim())
            ?: return null
    val day = match.groupValues[1].toIntOrNull() ?: return null
    val month = monthNumber(match.groupValues[2]) ?: return null
    val year =
        match.groupValues[3]
            .toIntOrNull()
            ?.let { year ->
                when (year) {
                    in 0..69 -> 2000 + year
                    in 70..99 -> 1900 + year
                    else -> year
                }
            } ?: return null
    val hour = match.groupValues[4].toIntOrNull() ?: return null
    val minute = match.groupValues[5].toIntOrNull() ?: return null
    val second = match.groupValues[6].toIntOrNull() ?: return null

    if (day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
        return null
    }

    return daysFromCivil(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second
}

private val CookieExpiresRegex =
    Regex(
        pattern = """(?i)(?:[a-z]{3},\s*)?(\d{1,2})[-\s]([a-z]{3})[-\s](\d{2,4})\s+(\d{1,2}):(\d{2}):(\d{2})\s*(?:gmt|utc)?""",
    )

private fun monthNumber(value: String): Int? =
    when (value.lowercase()) {
        "jan" -> 1
        "feb" -> 2
        "mar" -> 3
        "apr" -> 4
        "may" -> 5
        "jun" -> 6
        "jul" -> 7
        "aug" -> 8
        "sep" -> 9
        "oct" -> 10
        "nov" -> 11
        "dec" -> 12
        else -> null
    }

private fun daysFromCivil(
    year: Int,
    month: Int,
    day: Int,
): Long {
    val adjustedYear = year - if (month <= 2) 1 else 0
    val era = adjustedYear / 400
    val yearOfEra = adjustedYear - era * 400
    val monthPrime = month + if (month > 2) -3 else 9
    val dayOfYear = (153 * monthPrime + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146_097L + dayOfEra - 719_468L
}

private class VVOHeaderConfig {
    var chocolateProvider: (suspend () -> String?)? = null
}

private val VVOHeaderPlugin =
    createClientPlugin("VVOHeaderPlugin", ::VVOHeaderConfig) {
        val chocolateProvider = pluginConfig.chocolateProvider
        onRequest { request, _ ->
            chocolateProvider?.let { provider ->
                val chocolate = provider()
                if (!chocolate.isNullOrBlank()) {
                    request.headers.append("Cookie", chocolate)
                }
            }
            request.headers.append("Referer", "https://$vvoHost/")
        }
    }
