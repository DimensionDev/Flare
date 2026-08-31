package dev.dimension.flare.data.network.fanbox

import dev.dimension.flare.common.JSON
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxCreatorPostResponseTest {
    @Test
    fun creatorPostPagesAreDecodedFromPageUrls() {
        val response =
            JSON.decodeFromString(
                FanboxCreatorPostPagesResponse.serializer(),
                """{"body":{"pageUrls":["https://api.fanbox.cc/post.listCreator?creatorId=sample"]}}""",
            )

        assertEquals(1, response.body.pageUrls.size)
    }

    @Test
    fun creatorPostsAreDecodedFromPosts() {
        val response =
            JSON.decodeFromString(
                FanboxCreatorPostListResponse.serializer(),
                """{"body":{"posts":[{"id":"sample-post","title":"Sample post"}]}}""",
            )

        assertEquals(
            "sample-post",
            response.body.posts
                .single()
                .id,
        )
    }
}
