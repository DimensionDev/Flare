package dev.dimension.flare.ui.route

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalSerializationApi::class)
class MediaDeeplinkProxyTest {
    private val account = MicroBlogKey("alice", "example.social")
    private val image =
        DeeplinkRoute.Media.Image(
            uri = "https://cdn.example/image.jpg",
            previewUrl = "https://cdn.example/preview.jpg",
            customHeaders = mapOf("Authorization" to "Bearer private"),
            proxy = ProxyRef.Account(account),
        )

    @Test
    fun externalMediaCannotChooseSystemOrAccountRouting() {
        for (proxy in listOf(ProxyRef.System, ProxyRef.Account(account), ProxyRef.Unbound)) {
            assertNull(DeeplinkRoute.parse(externalUri(image.copy(proxy = proxy))))
        }
    }

    @Test
    fun externalAccountPickerCannotHideScopedMedia() {
        val nested = picker(picker(image))
        assertNull(DeeplinkRoute.parse(externalUri(nested)))
    }

    @Test
    fun aForeignSignatureCannotAuthorizeMediaRouting() {
        assertNull(DeeplinkRoute.parse(externalUri(image, signature = "00".repeat(32))))
    }

    @Test
    fun unsignedGlobalMediaRemainsCompatible() {
        val global = image.copy(proxy = ProxyRef.Global)
        assertEquals(global, DeeplinkRoute.parse(externalUri(global)))
        assertEquals(picker(global), DeeplinkRoute.parse(externalUri(picker(global))))
    }

    @Test
    fun internalMediaPreservesProxyHeadersAndPreview() {
        for (proxy in listOf(ProxyRef.System, ProxyRef.Account(account))) {
            val scoped = image.copy(proxy = proxy)
            assertEquals(scoped, DeeplinkRoute.parse(scoped.toUri()))
            assertEquals(picker(scoped), DeeplinkRoute.parse(picker(scoped).toUri()))
        }
    }

    @Test
    fun changingSignedMediaCannotReuseItsRoutingPermission() {
        val signed = ProtoBuf.decodeFromHexString<ExternalRoutePackage>(image.toUri().removePrefix("flare://"))
        val changed =
            listOf(
                image.copy(uri = "https://attacker.example/image.jpg"),
                image.copy(previewUrl = "https://attacker.example/preview.jpg"),
                image.copy(customHeaders = mapOf("Authorization" to "Bearer changed")),
                image.copy(proxy = ProxyRef.System),
                image.copy(proxy = ProxyRef.Account(MicroBlogKey("bob", "example.social"))),
                picker(image),
            )
        for (route in changed) {
            assertNull(DeeplinkRoute.parse(externalUri(route, signed.signature)))
        }
    }

    private fun picker(route: DeeplinkRoute): DeeplinkRoute =
        DeeplinkRoute.DeepLinkAccountPicker("https://example.social/image", mapOf(account to route))

    private fun externalUri(
        route: DeeplinkRoute,
        signature: String? = null,
    ): String = "flare://${ProtoBuf.encodeToHexString(ExternalRoutePackage(route, signature))}"

    // Encode the public wire format without using the app's trusted link builder.
    @Serializable
    private data class ExternalRoutePackage(
        val route: DeeplinkRoute,
        val signature: String? = null,
    )
}
