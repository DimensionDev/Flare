package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.network.xqt.XQTMutationQueryIds
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweetRequest
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweetRequestVariables
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestFeatures
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestVariables
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestVariablesMedia
import dev.dimension.flare.data.network.xqt.model.PostDeleteRetweetRequest
import dev.dimension.flare.data.network.xqt.model.PostDeleteRetweetRequestVariables
import kotlin.test.Test
import kotlin.test.assertEquals

class XQTMutationQueryIdsTest {
    @Test
    fun mutationRequestDefaultsMatchEndpointQueryIds() {
        assertEquals("mbRO74GrOvSfRcJnlMapnQ", XQTMutationQueryIds.CREATE_RETWEET)
        assertEquals("5CdvsV_zjv4L64XFifAglw", XQTMutationQueryIds.CREATE_TWEET)
        assertEquals("ZyZigVsNiFO6v1dEks1eWg", XQTMutationQueryIds.DELETE_RETWEET)

        assertEquals(
            XQTMutationQueryIds.CREATE_RETWEET,
            PostCreateRetweetRequest(variables = PostCreateRetweetRequestVariables()).queryId,
        )
        assertEquals(
            XQTMutationQueryIds.CREATE_TWEET,
            PostCreateTweetRequest(
                features = PostCreateTweetRequestFeatures(),
                variables =
                    PostCreateTweetRequestVariables(
                        media = PostCreateTweetRequestVariablesMedia(mediaEntities = emptyList()),
                        semanticAnnotationIds = emptyList(),
                    ),
            ).queryId,
        )
        assertEquals(
            XQTMutationQueryIds.DELETE_RETWEET,
            PostDeleteRetweetRequest(variables = PostDeleteRetweetRequestVariables()).queryId,
        )
    }
}
