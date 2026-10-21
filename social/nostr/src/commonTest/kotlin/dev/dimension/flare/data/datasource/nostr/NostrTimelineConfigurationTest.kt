package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NostrTimelineConfigurationTest {
    @Test
    fun chronologicalHomeCanJoinItsCachedBoundary() =
        runTest {
            stopKoin()
            startKoin { modules(module { single<CoroutineScope> { backgroundScope } }) }
            try {
                // An empty credential flow never creates a client or connects to a relay.
                val source = NostrDataSource(MicroBlogKey("test", "nostr"), emptyFlow())
                try {
                    assertTrue(assertIs<CacheableRemoteLoader<*>>(source.homeTimeline()).canJoinTimeline)
                } finally {
                    source.close()
                }
            } finally {
                stopKoin()
            }
        }
}
