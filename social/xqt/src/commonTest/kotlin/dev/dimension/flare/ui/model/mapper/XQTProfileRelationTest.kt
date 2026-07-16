package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.xqt.model.GetProfileSpotlightsQuery200Response
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XQTProfileRelationTest {
    @Test
    fun profileSpotlightsWithoutLegacyUsesRelationshipPerspectives() {
        val response =
            """
            {
              "data": {
                "user_result_by_screen_name": {
                  "id": "VXNlcjoxNTEyODc4MjQ=",
                  "result": {
                    "__typename": "User",
                    "core": {
                      "name": "Test user",
                      "screen_name": "test_user"
                    },
                    "id": "VXNlcjoxNTEyODc4MjQ=",
                    "privacy": {
                      "protected": false
                    },
                    "profilemodules": {
                      "v1": []
                    },
                    "relationship_perspectives": {
                      "blocked_by": false,
                      "blocking": false,
                      "followed_by": false,
                      "following": true
                    },
                    "rest_id": "151287824"
                  }
                }
              }
            }
            """.trimIndent().decodeJson<GetProfileSpotlightsQuery200Response>()

        val relation = response.toUi(muting = true)

        assertTrue(relation.following)
        assertFalse(relation.isFans)
        assertFalse(relation.blocking)
        assertFalse(relation.blockedBy)
        assertTrue(relation.muted)
    }
}
