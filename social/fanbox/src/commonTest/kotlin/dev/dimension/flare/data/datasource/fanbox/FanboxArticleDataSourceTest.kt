package dev.dimension.flare.data.datasource.fanbox

import dev.dimension.flare.data.platform.FanboxCredential
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FanboxArticleDataSourceTest {
    @Test
    fun articleCommentsUsesDedicatedCommentsLoader() {
        val accountKey = MicroBlogKey(id = "account", host = "fanbox.cc")
        val articleKey = MicroBlogKey(id = "article", host = "fanbox.cc")
        val dataSource =
            FanboxDataSource(
                accountKey = accountKey,
                credentialFlow =
                    flowOf(
                        FanboxCredential(
                            sessionId = "session",
                            userId = "user",
                        ),
                    ),
                updateCredential = {},
            )

        val loader = assertIs<FanboxCommentsLoader>(dataSource.articleComments(articleKey))

        assertEquals("fanbox_comments_${articleKey}_$accountKey", loader.pagingKey)
    }
}
