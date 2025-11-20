package dev.dimension.flare.ui.presenter.home.rss

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ImportOPMLPresenterTest {
    private lateinit var db: AppDatabase

    @BeforeTest
    fun setup() {
        val db =
            Room
                .inMemoryDatabaseBuilder<AppDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        this.db = db

        startKoin {
            modules(
                module {
                    single { db }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun testImportOPML() =
        runTest {
            val opmlContent =
                """
                <opml version="2.0">
                    <head>
                        <title>My Feeds</title>
                    </head>
                    <body>
                        <outline text="Tech" title="Tech">
                            <outline type="rss" text="The Verge" title="The Verge" xmlUrl="https://www.theverge.com/rss/index.xml" htmlUrl="https://www.theverge.com/"/>
                        </outline>
                        <outline type="rss" text="Daring Fireball" title="Daring Fireball" xmlUrl="https://daringfireball.net/feeds/main" htmlUrl="https://daringfireball.net/"/>
                    </body>
                </opml>
                """.trimIndent()

            val presenter = ImportOPMLPresenter(opmlContent) { null }

            val states = mutableListOf<ImportOPMLPresenter.State>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            job.cancel()

            val finalState = states.last()

            println(
                "Final state: importing=${finalState.importing}, error=${finalState.error}, total=${finalState.totalCount}, imported=${finalState.importedCount}",
            )

            assertFalse(finalState.importing)
            assertNull(finalState.error)
            assertEquals(2, finalState.totalCount)
            assertEquals(2, finalState.importedCount)

            val sources = db.rssSourceDao().getAll().first()
            assertEquals(2, sources.size)

            val verge = sources.find { it.url == "https://www.theverge.com/rss/index.xml" }
            assertEquals("The Verge", verge?.title)

            val df = sources.find { it.url == "https://daringfireball.net/feeds/main" }
            assertEquals("Daring Fireball", df?.title)
        }

    @Test
    fun testImportOPMLWithExistingUrl() =
        runTest {
            // Pre-populate DB with one source
            val existingUrl = "https://www.theverge.com/rss/index.xml"
            val existingSource =
                DbRssSources(
                    url = existingUrl,
                    title = "Old Title",
                    icon = null,
                    lastUpdate = 123456789L,
                )
            db.rssSourceDao().insert(existingSource)

            val opmlContent =
                """
                <opml version="2.0">
                    <head>
                        <title>My Feeds</title>
                    </head>
                    <body>
                        <outline text="Tech" title="Tech">
                            <outline type="rss" text="The Verge" title="New Title" xmlUrl="$existingUrl" htmlUrl="https://www.theverge.com/"/>
                        </outline>
                        <outline type="rss" text="Daring Fireball" title="Daring Fireball" xmlUrl="https://daringfireball.net/feeds/main" htmlUrl="https://daringfireball.net/"/>
                    </body>
                </opml>
                """.trimIndent()

            val presenter = ImportOPMLPresenter(opmlContent) { null }

            val states = mutableListOf<ImportOPMLPresenter.State>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            job.cancel()

            val finalState = states.last()

            assertFalse(finalState.importing)
            assertNull(finalState.error)
            assertEquals(2, finalState.totalCount)
            assertEquals(2, finalState.importedCount)

            val sources = db.rssSourceDao().getAll().first()
            assertEquals(2, sources.size)

            val verge = sources.find { it.url == existingUrl }
            // Should retain the OLD title because we skipped insertion
            assertEquals("Old Title", verge?.title)

            val df = sources.find { it.url == "https://daringfireball.net/feeds/main" }
            assertEquals("Daring Fireball", df?.title)
        }
}
