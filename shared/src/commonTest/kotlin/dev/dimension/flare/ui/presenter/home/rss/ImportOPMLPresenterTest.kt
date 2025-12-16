package dev.dimension.flare.ui.presenter.home.rss

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.ui.humanizer.PlatformFormatter
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ImportOPMLPresenterTest {
    class TestFormatter : PlatformFormatter {
        override fun formatNumber(number: Long): String = number.toString()

        override fun formatRelativeInstant(instant: Instant): String = instant.toString()

        override fun formatFullInstant(instant: Instant): String = instant.toString()
    }

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
                    single<PlatformFormatter> { TestFormatter() }
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

    @Test
    fun testConcurrencyStressTest() =
        runTest {
            val feedCount = 1000
            val sb = StringBuilder()
            sb.append("""<opml version="2.0"><head><title>Stress Test</title></head><body>""")

            repeat(feedCount) { i ->
                sb.append(
                    """
                    <outline type="rss" text="Feed $i" title="Title $i" xmlUrl="https://stress.test/feed/$i.xml" />
                    """.trimIndent(),
                )
            }
            sb.append("</body></opml>")

            val presenter =
                ImportOPMLPresenter(sb.toString()) { url ->
                    kotlinx.coroutines.delay((1..10).random().toLong())
                    "https://icon.url/icon.png"
                }

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
            assertNull(finalState.error, "Should verify concurrency without errors")
            assertFalse(finalState.importing, "Should finish importing")
            assertEquals(feedCount, finalState.totalCount, "Total count should match input")
            assertEquals(feedCount, finalState.importedCount, "Imported count should match input")
            assertEquals(
                feedCount,
                finalState.importedSources.size,
                "UiState list size should match input",
            )
            val dbSources = db.rssSourceDao().getAll().first()
            assertEquals(feedCount, dbSources.size, "Database records should match input")
        }
}
