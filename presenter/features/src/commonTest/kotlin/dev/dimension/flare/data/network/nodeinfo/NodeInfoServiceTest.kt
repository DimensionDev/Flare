package dev.dimension.flare.data.network.nodeinfo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeInfoServiceTest {
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
