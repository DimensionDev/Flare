package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.model.MicroBlogKey
import org.junit.Assert
import org.junit.Test

class MastodonMappingTest {
    @Test
    fun mappingTimeline() {
        val statusJson = this.javaClass.classLoader?.getResource("mastodon_status.json")?.readText()
        Assert.assertNotNull(statusJson)
        val status = statusJson?.decodeJson<List<Status>>()
        Assert.assertNotNull(status)
        val accountKey = MicroBlogKey("123", "test")
        val pagingKey = "test"
        val timeline =
            status?.map {
                it.toDbPagingTimeline(accountKey, pagingKey)
            }
        Assert.assertNotNull(timeline)
        Assert.assertEquals("105853517700650526", timeline?.get(0)?.status_key?.id)
    }
}
