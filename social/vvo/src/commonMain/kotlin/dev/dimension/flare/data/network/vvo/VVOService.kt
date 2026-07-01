package dev.dimension.flare.data.network.vvo

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.ktorfit
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
import dev.dimension.flare.model.vvoHost
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private val baseUrl = "https://$vvoHost/"
private val chocolateRefreshInterval = 1.days

private fun vvoKtorfit(
    url: String = baseUrl,
    chocolateProvider: suspend () -> String?,
) = ktorfit(url) {
    install(VVOHeaderPlugin) {
        this.chocolateProvider = chocolateProvider
    }
}

private class VVOApis(
    chocolateProvider: suspend () -> String?,
) {
    private val ktorfit = vvoKtorfit(chocolateProvider = chocolateProvider)
    val timelineApi: TimelineApi = ktorfit.createTimelineApi()
    val userApi: UserApi = ktorfit.createUserApi()
    val configApi: ConfigApi = ktorfit.createConfigApi()
    val statusApi: StatusApi = ktorfit.createStatusApi()
}

private class VVOChocolateState(
    private val chocolateFlow: Flow<String>,
    private val lastChocolateRefreshEpochMillisFlow: Flow<Long?>?,
) {
    private val refreshedChocolate = MutableStateFlow<String?>(null)
    private val refreshedAt = MutableStateFlow<Long?>(null)
    val refreshTrackingEnabled: Boolean = lastChocolateRefreshEpochMillisFlow != null

    suspend fun currentChocolate(): String? = refreshedChocolate.value ?: chocolateFlow.firstOrNull()

    suspend fun currentRefreshEpochMillis(): Long? = refreshedAt.value ?: lastChocolateRefreshEpochMillisFlow?.firstOrNull()

    fun cache(
        chocolate: String,
        refreshedAtEpochMillis: Long,
    ) {
        refreshedChocolate.value = chocolate
        refreshedAt.value = refreshedAtEpochMillis
    }
}

internal class VVOService private constructor(
    private val chocolateState: VVOChocolateState,
    private val onChocolateRefreshed: suspend (String, Long) -> Unit,
    private val apis: VVOApis,
) : TimelineApi by apis.timelineApi,
    UserApi by apis.userApi,
    ConfigApi by apis.configApi,
    StatusApi by apis.statusApi {
    constructor(
        chocolateFlow: Flow<String>,
        lastChocolateRefreshEpochMillisFlow: Flow<Long?>? = null,
        onChocolateRefreshed: suspend (String, Long) -> Unit = { _, _ -> },
    ) : this(
        chocolateState =
            VVOChocolateState(
                chocolateFlow = chocolateFlow,
                lastChocolateRefreshEpochMillisFlow = lastChocolateRefreshEpochMillisFlow,
            ),
        onChocolateRefreshed = onChocolateRefreshed,
    )

    private constructor(
        chocolateState: VVOChocolateState,
        onChocolateRefreshed: suspend (String, Long) -> Unit,
    ) : this(
        chocolateState = chocolateState,
        onChocolateRefreshed = onChocolateRefreshed,
        apis = VVOApis(chocolateState::currentChocolate),
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
        refreshChocolateIfNeeded()

        val response = apis.configApi.config()
        if (response.data?.login == true) {
            return response
        }

        val refreshedChocolate = refreshChocolate() ?: return response
        return if (refreshedChocolate.isBlank()) {
            response
        } else {
            apis.configApi.config()
        }
    }

    suspend fun getUid(screenName: String): String? {
        val response =
            ktorClient {
                followRedirects = false
                install(VVOHeaderPlugin) {
                    chocolateProvider = chocolateState::currentChocolate
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
        ktorClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 2.minutes.inWholeMilliseconds
                requestTimeoutMillis = 2.minutes.inWholeMilliseconds
                socketTimeoutMillis = 2.minutes.inWholeMilliseconds
            }
            install(VVOHeaderPlugin) {
                chocolateProvider = chocolateState::currentChocolate
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

    suspend fun emojis(): EmojiData = ktorClient().get("https://flareapp.moe/emoji.json").body()

    private suspend fun refreshChocolateIfNeeded() {
        if (!chocolateState.refreshTrackingEnabled) {
            return
        }
        val lastRefreshEpochMillis = chocolateState.currentRefreshEpochMillis()
        val now = Clock.System.now().toEpochMilliseconds()
        if (shouldRefreshVvoCookie(lastRefreshEpochMillis, now)) {
            runCatching {
                refreshChocolate(refreshedAtEpochMillis = now)
            }
        }
    }

    private suspend fun refreshChocolate(refreshedAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds()): String? =
        refreshMutex.withLock {
            val currentChocolate =
                chocolateState
                    .currentChocolate()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@withLock null

            val response =
                ktorClient {
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

            chocolateState.cache(
                chocolate = refreshedChocolate,
                refreshedAtEpochMillis = refreshedAtEpochMillis,
            )
            onChocolateRefreshed(refreshedChocolate, refreshedAtEpochMillis)
            refreshedChocolate
        }
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
