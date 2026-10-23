package dev.dimension.flare.data.database.cache

import androidx.room3.Room
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.mapper.upsertUser
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.model.withProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProxyUserDedupTest {
    @Test
    fun bindingViewerProxyMustNotInvalidateIdenticalPersistedUser(): Unit =
        runBlocking {
            val db =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            val unbound = createSampleUser().withProxy(ProxyRef.Unbound)
            val bound = unbound.withProxy(ProxyRef.Account(MicroBlogKey("dedup-viewer", "example.social")))
            val emissions = Channel<Unit>(Channel.UNLIMITED)
            try {
                db.upsertUser(unbound.toDbUser())
                val observer =
                    launch(Dispatchers.Unconfined) {
                        db.userDao().findByKey(unbound.key).collect { emissions.send(Unit) }
                    }
                try {
                    withTimeout(5_000) { emissions.receive() }
                    assertNull(withTimeoutOrNull(250) { emissions.receive() }, "Initial insertion must be settled")

                    db.upsertUser(unbound.toDbUser())
                    assertNull(
                        withTimeoutOrNull(500) { emissions.receive() },
                        "Control: unchanged unbound user must not trigger a Room invalidation",
                    )

                    // Only transient proxy fields differ; persistent bytes remain identical.
                    db.upsertUser(bound.toDbUser())
                    assertNull(
                        withTimeoutOrNull(500) { emissions.receive() },
                        "Binding viewer proxy must not rewrite identical user content",
                    )
                    assertEquals(
                        ProxyRef.Unbound,
                        bound
                            .toDbUser()
                            .content.avatar
                            ?.proxy,
                    )
                    assertEquals(ProxyRef.Account(MicroBlogKey("dedup-viewer", "example.social")), bound.avatar?.proxy)

                    val changed = bound.copy(avatar = bound.avatar?.copy(url = "https://example.social/new-avatar.png"))
                    db.upsertUser(changed.toDbUser())
                    assertNotNull(withTimeout(5_000) { emissions.receive() }, "Persistent user changes must still invalidate Room")
                    assertEquals(
                        changed.avatar?.url,
                        db
                            .userDao()
                            .getByKeys(listOf(bound.key))
                            .single()
                            .content.avatar
                            ?.url,
                    )
                } finally {
                    observer.cancelAndJoin()
                }
            } finally {
                db.close()
            }
        }
}
