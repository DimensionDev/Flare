package dev.dimension.flare.feature.plugin.routing

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.manifest.PluginManifestV1
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PluginManifestDeepLinkRouterV1Test {
    private val router = PluginManifestDeepLinkRouterV1(PluginJsonV1.decodeFromString<PluginManifestV1>(MANIFEST))

    @Test
    fun routesCapturedProfilePostTimelineAndBrowserWithoutRuntime() {
        assertEquals(
            PluginManifestRouteV1.Profile("alice", "pixelfed.example"),
            router.route("https://pixelfed.example/alice", ORIGIN),
        )
        assertEquals(
            PluginManifestRouteV1.Post("ABC123", "pixelfed.example"),
            router.route("https://pixelfed.example/p/ABC123?ref=external", ORIGIN),
        )
        assertEquals(
            PluginManifestRouteV1.Timeline("local"),
            router.route("https://pixelfed.example/timeline/local", ORIGIN),
        )
        assertIs<PluginManifestRouteV1.Browser>(
            router.route("https://links.example/docs/start", ORIGIN),
        )
    }

    @Test
    fun rejectsWrongOriginSchemePathAndCredentials() {
        assertNull(router.route("https://other.example/alice", ORIGIN))
        assertNull(router.route("http://pixelfed.example/alice", ORIGIN))
        assertNull(router.route("https://pixelfed.example/p/ABC/extra", ORIGIN))
        assertNull(router.route("https://user:password@pixelfed.example/alice", ORIGIN))
    }
}

private const val ORIGIN = "https://pixelfed.example"
private const val MANIFEST =
    """
    {
      "schemaVersion": 1,
      "apiVersion": 1,
      "id": "dev.dimension.flare.test.routes",
      "version": "1.0.0",
      "name": "Routes",
      "permissions": { "authOrigins": ["https://links.example"] },
      "platform": {
        "id": "Routes",
        "name": "Routes",
        "deepLinks": [
          {
            "origin": "${'$'}accountOrigin",
            "path": [{ "type": "capture", "name": "username" }],
            "target": { "type": "Profile", "value": "{username}" }
          },
          {
            "origin": "${'$'}accountOrigin",
            "path": [{ "type": "literal", "value": "p" }, { "type": "capture", "name": "id" }],
            "target": { "type": "Post", "value": "{id}" }
          },
          {
            "origin": "${'$'}accountOrigin",
            "path": [{ "type": "literal", "value": "timeline" }, { "type": "literal", "value": "local" }],
            "target": { "type": "Timeline", "value": "local" }
          },
          {
            "origin": "https://links.example",
            "path": [{ "type": "literal", "value": "docs" }, { "type": "capture", "name": "page" }],
            "target": { "type": "Browser" }
          }
        ]
      }
    }
    """
