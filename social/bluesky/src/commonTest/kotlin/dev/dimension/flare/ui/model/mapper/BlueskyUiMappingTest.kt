package dev.dimension.flare.ui.model.mapper

import app.bsky.richtext.Facet
import app.bsky.richtext.FacetByteSlice
import app.bsky.richtext.FacetFeatureUnion
import app.bsky.richtext.FacetLink
import app.bsky.richtext.FacetMention
import app.bsky.richtext.FacetTag
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlueskyUiMappingTest {
    private val accountKey = MicroBlogKey(id = "account", host = "bsky.social")

    @Test
    fun parseBlueskyJson_mapsFacetsToUiLinks() {
        val text = "https://example.com #tag @alice.bsky.social"
        val mentionDid = "did:plc:alice123"
        val facets =
            listOf(
                Facet(
                    index = FacetByteSlice(byteStart = 0, byteEnd = 19),
                    features = listOf(FacetFeatureUnion.Link(FacetLink(uri = Uri("https://example.com")))),
                ),
                Facet(
                    index = FacetByteSlice(byteStart = 20, byteEnd = 24),
                    features = listOf(FacetFeatureUnion.Tag(FacetTag(tag = "tag"))),
                ),
                Facet(
                    index = FacetByteSlice(byteStart = 25, byteEnd = 43),
                    features = listOf(FacetFeatureUnion.Mention(FacetMention(did = Did(mentionDid)))),
                ),
            )

        val json =
            bskyJson.encodeAsJsonContent(
                buildJsonObject {
                    put("text", JsonPrimitive(text))
                    put("facets", bskyJson.encodeToJsonElement(facets))
                },
            )

        val result = parseBlueskyJson(json, accountKey)
        val links =
            result.renderRuns
                .filterIsInstance<RenderContent.Text>()
                .flatMap { it.runs }
                .filterIsInstance<RenderRun.Text>()
                .mapNotNull { it.style.link }
        val expectedMentionUrl =
            DeeplinkRoute.Profile
                .User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = MicroBlogKey(id = mentionDid, host = accountKey.host),
                ).toUri()
        val expectedTagUrl =
            DeeplinkRoute
                .Search(
                    accountType = AccountType.Specific(accountKey),
                    query = "#tag",
                ).toUri()

        assertTrue(links.contains("https://example.com"))
        assertTrue(links.contains(expectedMentionUrl))
        assertTrue(links.contains(expectedTagUrl))
    }

    @Test
    fun parseBlueskyJson_withoutFacets_returnsPlainText() {
        val json =
            bskyJson.encodeAsJsonContent(
                buildJsonObject {
                    put("text", JsonPrimitive("plain text"))
                },
            )

        val result = parseBlueskyJson(json, accountKey)
        assertEquals("plain text", result.innerText)
        assertEquals("plain text", result.raw)
    }
}
