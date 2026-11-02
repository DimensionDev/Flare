package dev.dimension.flare.ui.render

import dev.dimension.flare.data.database.cache.model.DbStatusContent
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.model.withProxy
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class MediaImageLinkTest {
    private val account = MicroBlogKey("viewer", "weibo.cn")
    private val proxy = ProxyRef.Account(account)

    @Test
    fun cachedImageLinksKeepTheirTargetHeadersAndAccountWithoutPersistingSignatures() {
        val image = UiMedia.Image("https://cdn.example/image.jpg", persistentMapOf("Authorization" to "Bearer test"), proxy)
        val text =
            parseHtml("""<a href="${image.url}"><strong>查看图片</strong></a>""")
                .toUi(imageLinks = mapOf(image.url to image))
        val encoded = ProtoBuf.encodeToByteArray(text)
        val post =
            createSampleStatus(createSampleUser()).copy(
                accountType = AccountType.Specific(account),
                content = UiTranslatableText(text),
            )
        val stored = DbStatusContent.encode(post)
        val restored = assertIs<UiTimelineV2.Post>(DbStatusContent.decode(DbStatusContent.bytes(stored)).value)
        val block =
            assertIs<RenderContent.Text>(
                restored.content.original.renderRuns
                    .single(),
            )
        val run = assertIs<RenderRun.Text>(block.runs.single())
        val route = assertIs<DeeplinkRoute.Media.Image>(DeeplinkRoute.parse(requireNotNull(run.style.resolvedLink)))
        assertTrue(block.hasLink)
        assertTrue(run.style.bold)
        assertNull(run.style.link)
        assertEquals(image.url, route.uri)
        assertEquals(image.previewUrl, route.previewUrl)
        assertEquals(image.customHeaders, route.customHeaders)
        assertEquals(proxy, route.proxy)
        assertContentEquals(encoded, ProtoBuf.encodeToByteArray(restored.content.original))
        assertContentEquals(encoded, ProtoBuf.encodeToByteArray(text.withProxy(ProxyRef.Account(MicroBlogKey("other", "weibo.cn")))))
    }

    @Test
    fun bindingRawLinksDoesNotSignUntrustedMediaOverrides() {
        val route = DeeplinkRoute.Media.Image("https://attacker.example/image.jpg", null, proxy = ProxyRef.System)
        val forged = "flare://${ProtoBuf.encodeToHexString(UnsignedRoutePackage(route))}"
        val text = parseHtml("""<a href="$forged">image</a>""").toUi().withProxy(proxy)
        val run = assertIs<RenderRun.Text>(assertIs<RenderContent.Text>(text.renderRuns.single()).runs.single())
        assertNull(run.style.imageLink)
        assertEquals(forged, run.style.resolvedLink)
        assertNull(DeeplinkRoute.parse(requireNotNull(run.style.resolvedLink)))
    }

    @Serializable
    private data class UnsignedRoutePackage(
        val route: DeeplinkRoute,
    )
}
