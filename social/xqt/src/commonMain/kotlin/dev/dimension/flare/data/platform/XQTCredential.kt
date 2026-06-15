package dev.dimension.flare.data.platform

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.JSON
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Immutable
@Serializable
@SerialName("XQTCredential")
internal data class XQTCredential(
    val chocolate: String,
    val xchatIdentity: XChatIdentityCredential? = null,
)

@Immutable
@Serializable
internal data class XChatIdentityCredential(
    val userId: String,
    val version: String,
    val publicKeyB64: String,
    val signingPublicKeyB64: String,
    val identityPrivateJwk: JsonObject? = null,
    val signingPrivateJwk: JsonObject? = null,
    val registrationMethod: String? = null,
    val pinBacked: Boolean? = null,
)

internal fun XChatIdentityCredential.toJsonString(): String =
    JSON.encodeToString(
        XChatIdentityCredential.serializer(),
        this,
    )
