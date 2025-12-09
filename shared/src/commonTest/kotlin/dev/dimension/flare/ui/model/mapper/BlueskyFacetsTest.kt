package dev.dimension.flare.ui.model.mapper

import app.bsky.richtext.FacetFeatureUnion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BlueskyFacetsTest {
    @Test
    fun `creates facets for link tag and handle mention resolved to did`() =
        runTest {
            val content = "https://example.com #tag @example.com"
            val handleDid = "did:plc:abcd1234"

            val facets =
                parseBskyFacets(content) { handle ->
                    assertEquals("example.com", handle)
                    handleDid
                }

            assertEquals(3, facets.size)

            val link = facets[0]
            assertEquals(0L, link.index.byteStart)
            assertEquals(19L, link.index.byteEnd)
            val linkFeature = link.features.single()
            assertTrue(linkFeature is FacetFeatureUnion.Link)
            assertEquals("https://example.com", linkFeature.value.uri.uri)

            val tag = facets[1]
            assertEquals(20L, tag.index.byteStart)
            assertEquals(24L, tag.index.byteEnd)
            val tagFeature = tag.features.single()
            assertTrue(tagFeature is FacetFeatureUnion.Tag)
            assertEquals("tag", tagFeature.value.tag)

            val mention = facets[2]
            assertEquals(25L, mention.index.byteStart)
            assertEquals(37L, mention.index.byteEnd)
            val mentionFeature = mention.features.single()
            assertTrue(mentionFeature is FacetFeatureUnion.Mention)
            assertEquals("did:plc:abcd1234", mentionFeature.value.did.did)
        }

    @Test
    fun `ignores non facet tokens`() =
        runTest {
            val content = "plain text only"

            val facets = parseBskyFacets(content) { it }

            assertTrue(facets.isEmpty())
        }

    @Test
    fun `handles newline separated mention with resolved did`() =
        runTest {
            val content = "cancle @flarechess.bsky.social NOWWWWW"
            val did = "did:plc:nlqkornjkmp4mx5twudrqtmt"

            val facets =
                parseBskyFacets(content) { handle ->
                    assertEquals("flarechess.bsky.social", handle)
                    did
                }

            assertEquals(1, facets.size)
            val mention = facets.first()
            assertEquals(7L, mention.index.byteStart)
            assertEquals(30L, mention.index.byteEnd)
            val mentionFeature = mention.features.single()
            assertTrue(mentionFeature is FacetFeatureUnion.Mention)
            assertEquals(did, mentionFeature.value.did.did)
        }

    @Test
    fun `parses real post link and mention facets`() =
        runTest {
            val content =
                "I can't believe that after all these years - i get to update my issteamaudiooutyet.com website one last time. But here we are! @vrchat.com finally released Steam Audio for everyone, we're so back 🎉"
            val did = "did:plc:x4rerut4wifnwmbpcvshp5yw"

            val facets =
                parseBskyFacets(content) { handle ->
                    assertEquals("vrchat.com", handle)
                    did
                }

            assertEquals(2, facets.size)

            val link = facets[0]
            assertEquals(64L, link.index.byteStart)
            assertEquals(86L, link.index.byteEnd)
            val linkFeature = link.features.single()
            assertTrue(linkFeature is FacetFeatureUnion.Link)
            assertEquals("issteamaudiooutyet.com", linkFeature.value.uri.uri)

            val mention = facets[1]
            assertEquals(127L, mention.index.byteStart)
            assertEquals(138L, mention.index.byteEnd)
            val mentionFeature = mention.features.single()
            assertTrue(mentionFeature is FacetFeatureUnion.Mention)
            assertEquals(did, mentionFeature.value.did.did)
        }

    @Test
    fun `parses tag facet with newline`() =
        runTest {
            val content = "葬儀屋さん \n#NIGHTREIGN"

            val facets = parseBskyFacets(content) { handle -> handle }

            assertEquals(1, facets.size)
            val tag = facets.first()
            assertEquals(17L, tag.index.byteStart)
            assertEquals(28L, tag.index.byteEnd)
            val feature = tag.features.single()
            assertTrue(feature is FacetFeatureUnion.Tag)
            assertEquals("NIGHTREIGN", feature.value.tag)
        }
}
