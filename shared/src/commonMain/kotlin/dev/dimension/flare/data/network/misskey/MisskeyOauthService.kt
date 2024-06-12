package dev.dimension.flare.data.network.misskey

import com.benasher44.uuid.uuid4
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.misskey.api.AuthResources
import dev.dimension.flare.data.network.misskey.api.createAuthResources
import dev.dimension.flare.data.network.misskey.api.model.response.MiAuthCheckResponse
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments

private val defaultPermission =
    listOf(
        "read:account",
        "write:account",
        "read:blocks",
        "write:blocks",
        "read:drive",
        "write:drive",
        "read:favorites",
        "write:favorites",
        "read:following",
        "write:following",
        "read:messaging",
        "write:messaging",
        "read:mutes",
        "write:mutes",
        "write:notes",
        "read:notifications",
        "write:notifications",
        "write:reactions",
        "write:votes",
        "read:pages",
        "write:pages",
        "write:page-likes",
        "read:page-likes",
        "write:gallery-likes",
        "read:gallery-likes",
    )

internal class MisskeyOauthService(
    private val host: String,
    private val name: String? = null,
    private val icon: String? = null,
    private val callback: String? = null,
    private val permission: List<String> = defaultPermission,
    private val session: String = uuid4().toString(),
) : AuthResources by ktorfit("https://$host/").createAuthResources() {
    fun getAuthorizeUrl(): String {
        val url =
            URLBuilder().apply {
                protocol = URLProtocol.HTTPS
                this.host = this@MisskeyOauthService.host
                appendPathSegments("miauth", session)
                if (name != null) {
                    parameters.append("name", name)
                }
                if (icon != null) {
                    parameters.append("icon", icon)
                }
                if (callback != null) {
                    parameters.append("callback", callback)
                }
                parameters.append("permission", permission.joinToString(","))
            }
        return url.buildString()
    }

    suspend fun check(): MiAuthCheckResponse = check(session)
}
