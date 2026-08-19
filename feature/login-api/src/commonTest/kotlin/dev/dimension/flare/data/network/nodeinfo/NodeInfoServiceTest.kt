package dev.dimension.flare.data.network.nodeinfo

import dev.dimension.flare.data.network.nodeinfo.model.NodeInfo
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeInfoServiceTest {
    @Test
    fun nodeInfoPayloadCanBeDeserialized() {
        val nodeInfo =
            Json.decodeFromString<NodeInfo>(
                """{"links":[{"rel":"http://nodeinfo.diaspora.software/ns/schema/2.1","href":"https://example.com/nodeinfo/2.1"}]}""",
            )

        assertEquals("https://example.com/nodeinfo/2.1", nodeInfo.links.single().href)
    }

    @Test
    fun unsupportedSoftwareMatchesWafrnCaseInsensitively() {
        assertTrue(NodeInfoService.isUnsupportedSoftware("wafrn"))
        assertTrue(NodeInfoService.isUnsupportedSoftware("Wafrn"))
    }

    @Test
    fun unsupportedSoftwareDoesNotMatchSupportedForks() {
        assertFalse(NodeInfoService.isUnsupportedSoftware("mastodon"))
        assertFalse(NodeInfoService.isUnsupportedSoftware("akkoma"))
        assertFalse(NodeInfoService.isUnsupportedSoftware(null))
    }
}
