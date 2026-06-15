package dev.dimension.flare.data.network.xqt.xchat

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.platform.XChatIdentityCredential
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import xyz.juicebox.sdk.kmp.AuthToken
import xyz.juicebox.sdk.kmp.Client
import xyz.juicebox.sdk.kmp.Configuration
import xyz.juicebox.sdk.kmp.RealmId

internal suspend fun XChatService.recoverIdentityWithPin(
    identity: XChatIdentityCredential,
    pinCode: String,
): XChatIdentityCredential {
    val publicKey = recoverablePublicKey(identity.userId, identity.version)
    return recoverIdentityWithPin(
        base = identity,
        publicKey = publicKey,
        pinCode = pinCode,
    )
}

internal suspend fun XChatService.recoverIdentityWithPin(
    userId: String,
    pinCode: String,
): XChatIdentityCredential {
    val publicKey = recoverablePublicKey(userId, version = null)
    return recoverIdentityWithPin(
        base = publicKey.toIdentityCredential(userId),
        publicKey = publicKey,
        pinCode = pinCode,
    )
}

private suspend fun XChatService.recoverablePublicKey(
    userId: String,
    version: String?,
): XChatPublicKey =
    publicKeys(
        userIds = listOf(userId),
        includeJuiceboxTokens = true,
    ).firstOrNull()
        ?.keys
        .orEmpty()
        .firstOrNull { publicKey ->
            (version == null || publicKey.version == version) &&
                publicKey.hasJuiceboxTokens
        }
        ?: error("XChat public key metadata was not found")

private suspend fun XChatService.recoverIdentityWithPin(
    base: XChatIdentityCredential,
    publicKey: XChatPublicKey,
    pinCode: String,
): XChatIdentityCredential {
    val tokenMap =
        listOfNotNull(
            publicKey.tokenMap,
            publicKey.targetTokenMap,
        ).firstOrNull { it.entries.isNotEmpty() }
            ?: error("XChat Juicebox token map was not found")
    val client =
        Client(
            configuration = tokenMap.toJuiceboxConfiguration(),
            authTokens = tokenMap.toJuiceboxAuthTokens(),
        )
    val secret =
        client.recover(
            pin = pinCode.encodeToByteArray(),
        )
    return secret.toRecoveredIdentityCredential(
        base =
            base.copy(
                publicKeyB64 = publicKey.publicKey ?: base.publicKeyB64,
                signingPublicKeyB64 = publicKey.signingPublicKey ?: base.signingPublicKeyB64,
                registrationMethod = publicKey.registrationMethod ?: base.registrationMethod,
                pinBacked = true,
            ),
    )
}

private val XChatPublicKey.hasJuiceboxTokens: Boolean
    get() =
        tokenMap?.entries?.isNotEmpty() == true ||
            targetTokenMap?.entries?.isNotEmpty() == true

private fun XChatPublicKey.toIdentityCredential(userId: String): XChatIdentityCredential =
    XChatIdentityCredential(
        userId = userId,
        version = version ?: error("XChat public key metadata is missing version"),
        publicKeyB64 = publicKey ?: error("XChat public key metadata is missing public key"),
        signingPublicKeyB64 = signingPublicKey ?: error("XChat public key metadata is missing signing public key"),
        registrationMethod = registrationMethod,
        pinBacked = true,
    )

private fun XChatJuiceboxTokenMap.toJuiceboxConfiguration(): Configuration {
    val configurationJson =
        if (entries.isNotEmpty()) {
            buildJsonObject {
                put(
                    "realms",
                    buildJsonArray {
                        entries.forEach { entry ->
                            add(
                                buildJsonObject {
                                    put("id", entry.realmId)
                                    put("address", entry.address)
                                    entry.publicKey?.takeIf { it.isNotBlank() }?.let { put("public_key", it) }
                                },
                            )
                        }
                    },
                )
                put("register_threshold", registerThreshold ?: entries.size)
                put("recover_threshold", recoverThreshold ?: registerThreshold ?: entries.size)
                put("pin_hashing_mode", "Standard2019")
            }.toString()
        } else {
            listOfNotNull(
                keyStoreTokenMapJson,
                realmStateString,
            ).firstOrNull { it.trim().startsWith("{") }
                ?: error("XChat Juicebox token map did not contain realm configuration")
        }
    return Configuration(configurationJson)
}

private fun XChatJuiceboxTokenMap.toJuiceboxAuthTokens(): Map<RealmId, AuthToken> {
    val tokens =
        entries
            .mapNotNull { entry ->
                runCatching {
                    RealmId(entry.realmId) to AuthToken(entry.token)
                }.getOrNull()
            }.toMap()
    require(tokens.isNotEmpty()) { "XChat Juicebox token map did not contain usable auth tokens" }
    return tokens
}

internal fun ByteArray.toRecoveredIdentityCredential(base: XChatIdentityCredential): XChatIdentityCredential {
    val root =
        runCatching {
            JSON.parseToJsonElement(decodeToString())
        }.getOrNull()
    if (root != null) {
        return root.toRecoveredIdentityCredential(base)
    }
    rawP256ScalarsOrNull()?.let { scalars ->
        return scalars.toRecoveredIdentityCredentialFromScalars(base)
    }
    error("Recovered XChat identity secret is not JSON or raw P-256 scalar data")
}

internal fun ByteArray.rawP256ScalarsOrNull(): ByteArray? {
    if (size == XCHAT_JUICEBOX_SECRET_SIZE) {
        return this
    }
    val text = runCatching { decodeToString().trim() }.getOrNull() ?: return null
    return runCatching { XChatBase64.decode(text) }
        .getOrNull()
        ?.takeIf { it.size == XCHAT_JUICEBOX_SECRET_SIZE }
}

private fun ByteArray.toRecoveredIdentityCredentialFromScalars(base: XChatIdentityCredential): XChatIdentityCredential {
    val identityScalar = copyOfRange(0, P256_SCALAR_SIZE)
    val signingScalar = copyOfRange(P256_SCALAR_SIZE, XCHAT_JUICEBOX_SECRET_SIZE)
    return base.copy(
        identityPrivateJwk =
            p256PrivateJwk(
                privateScalar = identityScalar,
                publicKeyB64 = base.publicKeyB64,
            ),
        signingPrivateJwk =
            p256PrivateJwk(
                privateScalar = signingScalar,
                publicKeyB64 = base.signingPublicKeyB64,
            ),
        pinBacked = true,
    )
}

private fun p256PrivateJwk(
    privateScalar: ByteArray,
    publicKeyB64: String,
): JsonObject {
    val publicKey = XChatBase64.decode(publicKeyB64).p256UncompressedPublicKey()
    val x = publicKey.copyOfRange(1, 1 + P256_SCALAR_SIZE)
    val y = publicKey.copyOfRange(1 + P256_SCALAR_SIZE, P256_UNCOMPRESSED_PUBLIC_KEY_SIZE)
    return buildJsonObject {
        put("kty", "EC")
        put("crv", "P-256")
        put("d", XChatBase64.encodeUrl(privateScalar))
        put("x", XChatBase64.encodeUrl(x))
        put("y", XChatBase64.encodeUrl(y))
    }
}

private fun ByteArray.p256UncompressedPublicKey(): ByteArray =
    when {
        size == P256_UNCOMPRESSED_PUBLIC_KEY_SIZE && first() == P256_UNCOMPRESSED_PUBLIC_KEY_PREFIX -> this
        size > P256_UNCOMPRESSED_PUBLIC_KEY_SIZE &&
            this[size - P256_UNCOMPRESSED_PUBLIC_KEY_SIZE] == P256_UNCOMPRESSED_PUBLIC_KEY_PREFIX ->
            copyOfRange(size - P256_UNCOMPRESSED_PUBLIC_KEY_SIZE, size)
        else -> error("XChat public key is not an uncompressed P-256 public key")
    }

private const val P256_SCALAR_SIZE = 32
private const val P256_UNCOMPRESSED_PUBLIC_KEY_SIZE = 65
private const val P256_UNCOMPRESSED_PUBLIC_KEY_PREFIX: Byte = 0x04
private const val XCHAT_JUICEBOX_SECRET_SIZE = P256_SCALAR_SIZE * 2

private fun JsonElement.toRecoveredIdentityCredential(base: XChatIdentityCredential): XChatIdentityCredential {
    val identityElement =
        when (this) {
            is JsonObject ->
                this["xchatIdentity"]
                    ?: this["identity"]
                    ?: this["keyStore"]
                    ?: this
            else -> this
        }
    if (identityElement is JsonPrimitive) {
        val nestedJson = identityElement.contentOrNull?.takeIf { it.trim().startsWith("{") }
        if (nestedJson != null) {
            return JSON.parseToJsonElement(nestedJson).toRecoveredIdentityCredential(base)
        }
    }
    val decoded =
        runCatching {
            JSON.decodeFromJsonElement(
                XChatIdentityCredential.serializer(),
                identityElement,
            )
        }.getOrNull()
    if (decoded?.identityPrivateJwk != null && decoded.signingPrivateJwk != null) {
        return base.copy(
            identityPrivateJwk = decoded.identityPrivateJwk,
            signingPrivateJwk = decoded.signingPrivateJwk,
            registrationMethod = decoded.registrationMethod ?: base.registrationMethod,
            pinBacked = true,
        )
    }
    val identityObject = identityElement as? JsonObject ?: error("Recovered XChat identity secret is not an object")
    val privateKeys = identityObject.obj("privateKeys") ?: identityObject.obj("private_keys")
    val identityPrivateJwk =
        identityObject.firstObject(
            "identityPrivateJwk",
            "identity_private_jwk",
            "identityPrivateKeyJwk",
            "identity_private_key_jwk",
        ) ?: privateKeys?.firstObject(
            "identity",
            "identityPrivateJwk",
            "identity_private_jwk",
            "identityPrivateKeyJwk",
            "identity_private_key_jwk",
        ) ?: error("Recovered XChat identity secret is missing identity private JWK")
    val signingPrivateJwk =
        identityObject.firstObject(
            "signingPrivateJwk",
            "signing_private_jwk",
            "signingPrivateKeyJwk",
            "signing_private_key_jwk",
        ) ?: privateKeys?.firstObject(
            "signing",
            "signingPrivateJwk",
            "signing_private_jwk",
            "signingPrivateKeyJwk",
            "signing_private_key_jwk",
        ) ?: error("Recovered XChat identity secret is missing signing private JWK")
    return base.copy(
        identityPrivateJwk = identityPrivateJwk,
        signingPrivateJwk = signingPrivateJwk,
        pinBacked = true,
    )
}

private fun JsonObject.obj(name: String): JsonObject? =
    when (val value = this[name]) {
        JsonNull,
        null,
        -> null
        is JsonObject -> value
        else -> null
    }

private fun JsonObject.firstObject(vararg names: String): JsonObject? =
    names.firstNotNullOfOrNull { obj(it) }
