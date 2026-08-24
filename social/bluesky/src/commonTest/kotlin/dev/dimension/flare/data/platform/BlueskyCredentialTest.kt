package dev.dimension.flare.data.platform

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.Did
import sh.christian.ozone.oauth.DpopKeyPair
import sh.christian.ozone.oauth.OAuthScope
import sh.christian.ozone.oauth.OAuthToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class BlueskyCredentialTest {
    @Test
    fun legacyOAuthCredentialDefaultsToUnverifiedPds() =
        runTest {
            val credential: BlueskyCredential =
                BlueskyCredential.OAuthCredential(
                    baseUrl = "https://auth.example",
                    oAuthToken =
                        OAuthToken(
                            accessToken = "access-old",
                            refreshToken = "refresh-old",
                            keyPair = DpopKeyPair.generateKeyPair(),
                            expiresIn = 10.minutes,
                            scopes = listOf(OAuthScope.AtProto),
                            subject = Did("did:plc:alice"),
                            nonce = "nonce-old",
                            clientId = "https://client.example/metadata.json",
                            pdsUrl = "https://auth.example",
                        ),
                    pdsUrlVerified = true,
                )
            val currentJson =
                BlueskyJson
                    .encodeToJsonElement(BlueskyCredential.serializer(), credential)
                    .jsonObject
            assertEquals(JsonPrimitive(true), currentJson["pdsUrlVerified"])

            val legacyJson = JsonObject(currentJson - "pdsUrlVerified")
            val decoded =
                BlueskyJson.decodeFromJsonElement(
                    BlueskyCredential.serializer(),
                    legacyJson,
                ) as BlueskyCredential.OAuthCredential

            assertEquals(false, decoded.pdsUrlVerified)
        }
}
