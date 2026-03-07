package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.LoadState
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.datasource.microblog.loader.EmojiLoader
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.ui.model.UiEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmojiHandlerTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private lateinit var fakeLoader: FakeEmojiLoader
    private lateinit var handler: EmojiHandler

    private val host = "test.social"

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        fakeLoader = FakeEmojiLoader()

        startKoin {
            modules(
                module {
                    single { db }
                },
            )
        }

        handler =
            EmojiHandler(
                host = host,
                loader = fakeLoader,
            )
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun refreshWritesLoaderResultIntoDatabase() =
        runTest {
            val expected =
                persistentMapOf(
                    "People" to
                        persistentListOf(
                            createEmoji(shortcode = "smile", category = "People"),
                        ),
                )
            fakeLoader.nextResult = expected

            val state =
                handler.emoji.refreshState
                    .drop(1)
                    .first()
            assertTrue(state is LoadState.NotLoading)

            val saved = db.emojiDao().get(host).first()
            assertNotNull(saved)
            assertEquals(expected, saved.content.data)
        }

    @Test
    fun dataReadsCachedEmojiFromDatabase() =
        runTest {
            val cached =
                persistentMapOf(
                    "Food" to
                        persistentListOf(
                            createEmoji(shortcode = "apple", category = "Food"),
                        ),
                )
            db.emojiDao().insert(DbEmoji(host = host, content = EmojiContent(cached)))

            val state =
                handler.emoji.data
                    .filterIsInstance<CacheState.Success<ImmutableMap<String, ImmutableList<UiEmoji>>>>()
                    .first()
            assertEquals(cached, state.data)
        }

    @Test
    fun refreshFailureDoesNotOverrideExistingCache() =
        runTest {
            val existing =
                persistentMapOf(
                    "People" to
                        persistentListOf(
                            createEmoji(shortcode = "wave", category = "People"),
                        ),
                )
            db.emojiDao().insert(DbEmoji(host = host, content = EmojiContent(existing)))
            fakeLoader.shouldFail = true

            val state =
                handler.emoji.refreshState
                    .drop(1)
                    .first()
            assertTrue(state is LoadState.Error)

            val saved = db.emojiDao().get(host).first()
            assertNotNull(saved)
            assertEquals(existing, saved.content.data)
        }

    @Test
    fun refreshFailureWithNoCacheKeepsDatabaseEmpty() =
        runTest {
            fakeLoader.shouldFail = true

            val state =
                handler.emoji.refreshState
                    .drop(1)
                    .first()
            assertTrue(state is LoadState.Error)

            val saved = db.emojiDao().get(host).first()
            assertNull(saved)
        }

    private fun createEmoji(
        shortcode: String,
        category: String,
    ): UiEmoji =
        UiEmoji(
            shortcode = shortcode,
            url = "https://$host/$shortcode.png",
            category = category,
            searchKeywords = persistentListOf(shortcode),
            insertText = ":$shortcode:",
        )

    private class FakeEmojiLoader : EmojiLoader {
        var shouldFail = false
        var nextResult: ImmutableMap<String, ImmutableList<UiEmoji>> = persistentMapOf()

        override suspend fun emojis(): ImmutableMap<String, ImmutableList<UiEmoji>> {
            if (shouldFail) {
                error("loader failed")
            }
            return nextResult
        }
    }
}
