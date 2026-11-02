package dev.dimension.flare.feature.plugin.github

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.proxyHttpClientEngine
import dev.dimension.flare.feature.plugin.installer.FppLimits
import dev.dimension.flare.feature.plugin.installer.compareSemVer
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
public data class GitHubPluginRepositoryV1(
    val id: Long,
    val owner: String,
    val name: String,
) {
    val url: String get() = "https://github.com/$owner/$name"

    internal fun requireValid() {
        require(id > 0 && OWNER.matches(owner) && REPOSITORY.matches(name) && name != "." && name != "..") {
            "Invalid GitHub repository"
        }
    }
}

public data class GitHubPluginVersionV1(
    val tag: String,
    val version: String,
    val commitSha: String,
)

public data class GitHubPluginCatalogV1(
    val repository: GitHubPluginRepositoryV1,
    val versions: List<GitHubPluginVersionV1>,
)

@HiddenFromObjC
public class GitHubPluginClient(
    engine: HttpClientEngine = proxyHttpClientEngine(ProxyRef.Global),
) {
    private val client =
        HttpClient(engine) {
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }
        }
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val catalogs = mutableMapOf<String, Pair<Instant, GitHubPluginCatalogV1>>()
    private val retryAfter = MutableStateFlow<Instant?>(null)

    public suspend fun catalog(
        repositoryUrl: String,
        forceRefresh: Boolean = false,
        expectedRepositoryId: Long? = null,
    ): GitHubPluginCatalogV1 =
        mutex.withLock {
            val (owner, name) = parseRepositoryUrl(repositoryUrl)
            val key = "$owner/$name".lowercase()
            val cached = catalogs[key]
            if (!forceRefresh && cached != null && Clock.System.now() - cached.first < 5.minutes) {
                require(
                    expectedRepositoryId == null || cached.second.repository.id == expectedRepositoryId,
                ) { "GitHub repository identity changed" }
                return@withLock cached.second
            }
            catalogs.remove(key)
            val metadata = json.decodeFromString<RepositoryResponse>(request("https://api.github.com/repos/$owner/$name").decodeToString())
            require(!metadata.isPrivate) { "Only public GitHub repositories are supported" }
            val repository = GitHubPluginRepositoryV1(metadata.id, metadata.owner.login, metadata.name).also { it.requireValid() }
            require(expectedRepositoryId == null || repository.id == expectedRepositoryId) { "GitHub repository identity changed" }
            val versions = mutableListOf<GitHubPluginVersionV1>()
            var page = 1
            while (true) {
                val tags =
                    json.decodeFromString<List<TagResponse>>(
                        request(
                            "https://api.github.com/repos/${repository.owner}/${repository.name}/tags?per_page=100&page=$page",
                        ).decodeToString(),
                    )
                require(tags.size <= 100) { "GitHub returned too many tags in one page" }
                require(page <= 10 || tags.isEmpty()) { "Too many GitHub tags to determine the latest version" }
                tags.forEach { tag ->
                    val version = stableGitHubVersion(tag.name) ?: return@forEach
                    require(COMMIT_SHA.matches(tag.commit.sha)) { "Invalid GitHub tag commit" }
                    versions += GitHubPluginVersionV1(tag.name, version, tag.commit.sha)
                }
                if (tags.size < 100) break
                page++
            }
            require(versions.isNotEmpty()) { "This repository has no stable version tags" }
            require(versions.map { it.tag }.distinct().size == versions.size) { "Duplicate GitHub tags" }
            val catalog =
                GitHubPluginCatalogV1(
                    repository,
                    versions.sortedWith { first, second ->
                        compareSemVer(second.version, first.version).takeIf { it != 0 } ?: first.tag.compareTo(second.tag)
                    },
                )
            catalogs[key] = Clock.System.now() to catalog
            if (catalogs.size > 32) catalogs.remove(catalogs.keys.first())
            catalog
        }

    public suspend fun archive(
        repository: GitHubPluginRepositoryV1,
        version: GitHubPluginVersionV1,
    ): ByteArray {
        repository.requireValid()
        require(stableGitHubVersion(version.tag) == version.version && COMMIT_SHA.matches(version.commitSha)) { "Invalid GitHub version" }
        return request(
            "https://api.github.com/repos/${repository.owner}/${repository.name}/zipball/${version.commitSha}",
            limit = FppLimits.MAX_PACKAGE_BYTES,
        )
    }

    public fun close() {
        client.close()
    }

    private suspend fun request(
        initialUrl: String,
        limit: Long = 2L * 1024 * 1024,
    ): ByteArray =
        withTimeout(120_000) {
            require(retryAfter.value?.let { Clock.System.now() >= it } != false) { "GitHub is rate limited; try again later" }
            var url = initialUrl
            repeat(6) {
                val target = Url(url)
                require(
                    target.protocol == URLProtocol.HTTPS && target.port == 443 && target.user == null && target.password == null &&
                        target.host in setOf("api.github.com", "codeload.github.com"),
                ) { "Unsupported GitHub download redirect" }
                var redirect: String? = null
                val bytes =
                    client
                        .prepareGet(url) {
                            header(HttpHeaders.UserAgent, "Flare-Plugin-Installer")
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            header("X-GitHub-Api-Version", "2022-11-28")
                        }.execute { response ->
                            if (response.status.value in setOf(301, 302, 303, 307, 308)) {
                                redirect = requireNotNull(response.headers[HttpHeaders.Location]) { "Missing GitHub download redirect" }
                                return@execute null
                            }
                            if (response.status.value == 429 || response.status.value == 403) {
                                val now = Clock.System.now()
                                val seconds =
                                    response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
                                        ?: response.headers["X-RateLimit-Reset"]?.toLongOrNull()?.minus(now.epochSeconds)
                                        ?: 60L
                                retryAfter.value = now + seconds.coerceIn(1, 3_600).seconds
                                error("GitHub request was rate limited or denied; try again later")
                            }
                            require(response.status.value != 404) { "Public GitHub repository or version not found" }
                            require(response.status.value == 200) { "GitHub request failed (${response.status.value})" }
                            val channel = response.bodyAsChannel()
                            require(response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { it <= limit } != false) {
                                channel.cancel()
                                "GitHub response exceeds its size limit"
                            }
                            val packet = channel.readBuffer(limit + 1)
                            require(packet.size <= limit) {
                                channel.cancel()
                                "GitHub response exceeds its size limit"
                            }
                            packet.readByteArray()
                        }
                if (bytes != null) return@withTimeout bytes
                url = requireNotNull(redirect)
            }
            error("Too many GitHub download redirects")
        }
}

internal fun parseRepositoryUrl(value: String): Pair<String, String> {
    require(value.length <= 2_048) { "Invalid GitHub repository URL" }
    val url = Url(value.trim())
    require(
        url.protocol == URLProtocol.HTTPS && url.host == "github.com" && url.port == 443 &&
            url.user == null && url.password == null && url.parameters.isEmpty() && url.fragment.isEmpty(),
    ) { "Enter a public https://github.com/owner/repository URL" }
    val segments = url.encodedPath.trim('/').split('/')
    require(segments.size == 2) { "Enter a GitHub repository URL, without a branch or file path" }
    val owner = segments[0]
    val name = segments[1].removeSuffix(".git")
    GitHubPluginRepositoryV1(1, owner, name).requireValid()
    return owner to name
}

internal fun stableGitHubVersion(tag: String): String? {
    val version = tag.removePrefix("v")
    if (version.length > 128 || !STABLE_VERSION.matches(version)) return null
    if (version.substringBefore('+').split('.').any { it.toLongOrNull() == null }) return null
    return version
}

internal val COMMIT_SHA: Regex = Regex("[0-9a-f]{40}")
private val OWNER = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")
private val REPOSITORY = Regex("[A-Za-z0-9_.-]{1,100}")
private val STABLE_VERSION = Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?")

@Serializable
private data class RepositoryResponse(
    val id: Long,
    val name: String,
    val owner: OwnerResponse,
    @SerialName("private") val isPrivate: Boolean,
)

@Serializable
private data class OwnerResponse(
    val login: String,
)

@Serializable
private data class TagResponse(
    val name: String,
    val commit: CommitResponse,
)

@Serializable
private data class CommitResponse(
    val sha: String,
)
