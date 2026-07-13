package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.platform.XQTCredential
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertIs

class XQTArticleDataSourceTest {
    @Test
    fun articleCommentsUsesDedicatedCommentsLoader() {
        val dataSource =
            XQTDataSource(
                accountKey = MicroBlogKey(id = "account", host = "x.com"),
                sourceCredentialFlow = flowOf(XQTCredential(chocolate = "")),
            )

        val loader =
            dataSource.articleComments(
                articleKey = MicroBlogKey(id = "article", host = "x.com"),
            )

        assertIs<ArticleCommentsRemoteMediator>(loader)
    }
}
