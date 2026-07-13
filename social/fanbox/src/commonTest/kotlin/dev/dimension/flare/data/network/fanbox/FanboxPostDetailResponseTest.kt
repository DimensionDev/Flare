package dev.dimension.flare.data.network.fanbox

import dev.dimension.flare.common.JSON
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FanboxPostDetailResponseTest {
    @Test
    fun nestedPostBodyIsDecoded() {
        val response = JSON.decodeFromString(FanboxPostDetailResponse.serializer(), NESTED_RESPONSE)

        assertEquals("sample-post", response.body.id)
        assertEquals("Sample restricted post", response.body.title)
        assertEquals(100, response.body.feeRequired)
        assertEquals("2024-01-02T03:04:05+00:00", response.body.publishedDatetime)
        assertEquals("sample-creator", response.body.creatorId)
        assertEquals("Sample Creator", response.body.user?.name)
        assertTrue(response.body.isRestricted)
        assertTrue(response.body.hasAdultContent)
        assertFalse(response.body.isLiked)
        assertNull(response.body.body)
    }

    @Test
    fun flatPostBodyRemainsSupported() {
        val response = JSON.decodeFromString(FanboxPostDetailResponse.serializer(), FLAT_RESPONSE)

        assertEquals("legacy-post", response.body.id)
        assertEquals("Legacy response", response.body.title)
        assertEquals("2024-01-02T03:04:05+00:00", response.body.publishedDatetime)
        assertFalse(response.body.isRestricted)
        assertNull(response.body.body)
    }

    private companion object {
        val NESTED_RESPONSE =
            """
            {
              "body": {
                "post": {
                  "id": "sample-post",
                  "title": "Sample restricted post",
                  "feeRequired": 100,
                  "publishedDatetime": "2024-01-02T03:04:05+00:00",
                  "updatedDatetime": "2024-01-02T03:04:05+00:00",
                  "tags": [],
                  "isLiked": false,
                  "likeCount": 2,
                  "isCommentingRestricted": false,
                  "commentCount": 0,
                  "isRestricted": true,
                  "user": {
                    "userId": "sample-user",
                    "name": "Sample Creator",
                    "iconUrl": "https:\/\/example.com\/sample-user.png"
                  },
                  "creatorId": "sample-creator",
                  "hasAdultContent": true,
                  "type": "image",
                  "coverImageUrl": "https:\/\/example.com\/sample-cover.png",
                  "body": null,
                  "excerpt": "",
                  "nextPost": null,
                  "prevPost": {
                    "id": "sample-previous-post",
                    "title": "Sample previous post",
                    "publishedDatetime": "2024-01-01T03:04:05+00:00"
                  },
                  "imageForShare": "https:\/\/example.com\/sample-share.png",
                  "isPinned": false
                }
              }
            }
            """.trimIndent()

        val FLAT_RESPONSE =
            """
            {
              "body": {
                "id": "legacy-post",
                "title": "Legacy response",
                "publishedDatetime": "2024-01-02T03:04:05+00:00",
                "body": null
              }
            }
            """.trimIndent()
    }
}
