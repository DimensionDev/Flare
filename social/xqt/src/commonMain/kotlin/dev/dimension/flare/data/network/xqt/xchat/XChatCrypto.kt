package dev.dimension.flare.data.network.xqt.xchat

import dev.dimension.flare.data.platform.XChatIdentityCredential
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

internal data class XChatLoadedIdentity(
    val userId: String,
    val version: String,
    val publicKeySpki: ByteArray,
    val signingPublicKeyB64: String,
    val identityPrivateKey: ECDH.PrivateKey,
    val signingPrivateKey: ECDSA.PrivateKey,
    val conversationKeys: MutableMap<String, XChatConversationKey> = mutableMapOf(),
    val conversationTokens: MutableMap<String, String> = mutableMapOf(),
)

internal data class XChatConversationKey(
    val key: ByteArray,
    val version: String,
)

internal object XChatCrypto {
    suspend fun loadIdentity(identity: XChatIdentityCredential): XChatLoadedIdentity {
        val provider = CryptographyProvider.Default
        val ecdh = provider.get(ECDH)
        val ecdsa = provider.get(ECDSA)
        val identityPrivateJwk = identity.identityPrivateJwk ?: error("identityPrivateJwk is required")
        val signingPrivateJwk = identity.signingPrivateJwk ?: error("signingPrivateJwk is required")
        return XChatLoadedIdentity(
            userId = identity.userId,
            version = identity.version,
            publicKeySpki = XChatBase64.decode(identity.publicKeyB64),
            signingPublicKeyB64 = identity.signingPublicKeyB64,
            identityPrivateKey =
                ecdh
                    .privateKeyDecoder(EC.Curve.P256)
                    .decodeFromByteArray(
                        EC.PrivateKey.Format.RAW,
                        identityPrivateJwk.p256PrivateScalar("identityPrivateJwk"),
                    ),
            signingPrivateKey =
                ecdsa
                    .privateKeyDecoder(EC.Curve.P256)
                    .decodeFromByteArray(
                        EC.PrivateKey.Format.RAW,
                        signingPrivateJwk.p256PrivateScalar("signingPrivateJwk"),
                    ),
        )
    }

    suspend fun eciesWrap(
        conversationKey: ByteArray,
        recipientSpki: ByteArray,
    ): String {
        val provider = CryptographyProvider.Default
        val ecdh = provider.get(ECDH)
        val recipientPublicKey =
            ecdh
                .publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.DER, recipientSpki)
        val ephemeral = ecdh.keyPairGenerator(EC.Curve.P256).generateKey()
        val sharedSecret =
            ephemeral
                .privateKey
                .sharedSecretGenerator()
                .generateSharedSecretToByteArray(recipientPublicKey)
        val ephemeralRaw = ephemeral.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)
        val derived = sha256(sharedSecret + byteArrayOf(0, 0, 0, 1) + ephemeralRaw)
        val encrypted = aesGcmEncrypt(derived.copyOfRange(0, 16), derived.copyOfRange(16, 32), conversationKey)
        return XChatBase64.encode(ephemeralRaw + encrypted)
    }

    suspend fun eciesUnwrap(
        wrappedB64: String,
        identityPrivateKey: ECDH.PrivateKey,
    ): ByteArray {
        val wrapped = XChatBase64.decode(wrappedB64)
        val ephemeralRaw = wrapped.copyOfRange(0, 65)
        val ciphertext = wrapped.copyOfRange(65, wrapped.size)
        val ephemeralPublicKey =
            CryptographyProvider
                .Default
                .get(ECDH)
                .publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.RAW, ephemeralRaw)
        val sharedSecret =
            identityPrivateKey
                .sharedSecretGenerator()
                .generateSharedSecretToByteArray(ephemeralPublicKey)
        val derived = sha256(sharedSecret + byteArrayOf(0, 0, 0, 1) + ephemeralRaw)
        return aesGcmDecrypt(derived.copyOfRange(0, 16), derived.copyOfRange(16, 32), ciphertext)
    }

    fun encryptBody(
        plaintext: ByteArray,
        conversationKey: ByteArray,
    ): ByteArray =
        XChatSecretBox.encryptBody(
            plaintext = plaintext,
            key = conversationKey,
            nonce = secureRandomBytes(24),
        )

    fun decryptBody(
        frame: ByteArray,
        conversationKey: ByteArray,
    ): ByteArray? = XChatSecretBox.decryptBody(frame, conversationKey)

    suspend fun eventSignature(
        identity: XChatLoadedIdentity,
        conversationToken: String,
        conversationId: String,
        conversationKeyVersion: String,
        frame: ByteArray,
    ): String {
        val payload =
            listOf(
                "MessageCreateEvent",
                conversationToken,
                identity.userId,
                conversationId,
                conversationKeyVersion,
                XChatBase64.encodeUrl(frame),
            ).joinToString(",")
        val signature =
            identity
                .signingPrivateKey
                .signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW)
                .generateSignature(payload.encodeToByteArray())
        return XChatBase64.encode(
            XChatThrift.eventSignature(
                signatureBase64Url = XChatBase64.encodeUrl(signature),
                publicKeyVersion = identity.version,
                signingPublicKeyB64 = identity.signingPublicKeyB64,
                senderId = identity.userId,
            ),
        )
    }

    suspend fun actionSignature(
        identity: XChatLoadedIdentity,
        typeName: String,
        conversationToken: String,
        conversationId: String,
        dataElements: List<String>,
        eventDetailBytes: ByteArray? = null,
    ): JsonObject {
        val payload =
            (
                listOf(
                    typeName,
                    conversationToken,
                    identity.userId,
                    conversationId,
                ) + dataElements
            ).joinToString(",")
        val signature =
            identity
                .signingPrivateKey
                .signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW)
                .generateSignature(payload.encodeToByteArray())
        return buildJsonObject {
            put("chat_fanout_behavior_version", "V1")
            put("message_id", Uuid.random().toString())
            put(
                "message_event_signature",
                buildJsonObject {
                    put("signature", XChatBase64.encodeUrl(signature))
                    put("public_key_version", identity.version)
                    put("signature_version", "7")
                    put("signing_public_key", identity.signingPublicKeyB64)
                    put(
                        "message_signing_key_info_list",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("member_id", identity.userId)
                                    put("public_key_version", identity.version)
                                    put("signing_public_key", identity.signingPublicKeyB64)
                                },
                            )
                        },
                    )
                },
            )
            if (eventDetailBytes != null) {
                put("encoded_message_event_detail", XChatBase64.encodeUrl(eventDetailBytes))
            }
            put("signature_payload", payload)
        }
    }

    fun secureRandomBytes(size: Int): ByteArray = CryptographyRandom.nextBytes(size)

    suspend fun sha256(bytes: ByteArray): ByteArray =
        CryptographyProvider
            .Default
            .get(SHA256)
            .hasher()
            .hash(bytes)

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun aesGcmEncrypt(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
    ): ByteArray =
        CryptographyProvider
            .Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher()
            .encryptWithIv(iv, plaintext)

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun aesGcmDecrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray =
        CryptographyProvider
            .Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher()
            .decryptWithIv(iv, ciphertext)

    private fun JsonObject.p256PrivateScalar(fieldName: String): ByteArray {
        val encoded =
            this["d"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?: error("$fieldName.d is required")
        return XChatBase64
            .decodeUrl(encoded)
            .toP256Scalar(fieldName)
    }

    private fun ByteArray.toP256Scalar(fieldName: String): ByteArray =
        when {
            size == P256_PRIVATE_SCALAR_SIZE -> this
            size < P256_PRIVATE_SCALAR_SIZE ->
                ByteArray(P256_PRIVATE_SCALAR_SIZE).also { copyInto(it, P256_PRIVATE_SCALAR_SIZE - size) }
            size == P256_PRIVATE_SCALAR_SIZE + 1 && first() == 0.toByte() -> copyOfRange(1, size)
            else -> error("$fieldName.d must decode to a P-256 private scalar")
        }

    private const val P256_PRIVATE_SCALAR_SIZE = 32
}
