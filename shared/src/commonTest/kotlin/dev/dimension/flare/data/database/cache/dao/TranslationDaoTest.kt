package dev.dimension.flare.data.database.cache.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.profileTranslationEntityKey
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TranslationDaoTest : RobolectricTest() {
    private lateinit var db: CacheDatabase

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndFindStatusTranslation_roundTripsPayload() =
        runTest {
            val translation =
                DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey = "Specific(account@test.social)_status-1@test.social",
                    targetLanguage = "zh-CN",
                    sourceHash = "hash-1",
                    status = TranslationStatus.Completed,
                    payload =
                        TranslationPayload(
                            content = "你好".toUiPlainText(),
                            contentWarning = "剧透".toUiPlainText(),
                        ),
                    updatedAt = 123L,
                )

            db.translationDao().insert(translation)

            val saved =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Status,
                        entityKey = translation.entityKey,
                        targetLanguage = "zh-CN",
                    ).first()

            assertNotNull(saved)
            assertEquals(translation.sourceHash, saved.sourceHash)
            assertEquals(TranslationStatus.Completed, saved.status)
            assertEquals("你好", saved.payload?.content?.raw)
            assertEquals("剧透", saved.payload?.contentWarning?.raw)
        }

    @Test
    fun getByEntityKeys_filtersByLanguage() =
        runTest {
            val statusKey = "Specific(account@test.social)_status-2@test.social"
            db.translationDao().insertAll(
                listOf(
                    DbTranslation(
                        entityType = TranslationEntityType.Status,
                        entityKey = statusKey,
                        targetLanguage = "zh-CN",
                        sourceHash = "hash-1",
                        status = TranslationStatus.Completed,
                        payload = TranslationPayload(content = "中文".toUiPlainText()),
                        updatedAt = 1L,
                    ),
                    DbTranslation(
                        entityType = TranslationEntityType.Status,
                        entityKey = statusKey,
                        targetLanguage = "ja",
                        sourceHash = "hash-1",
                        status = TranslationStatus.Skipped,
                        statusReason = "same_language",
                        updatedAt = 2L,
                    ),
                ),
            )

            val zh =
                db.translationDao().getByEntityKeys(
                    entityType = TranslationEntityType.Status,
                    entityKeys = listOf(statusKey),
                    targetLanguage = "zh-CN",
                )
            val ja =
                db.translationDao().getByEntityKeys(
                    entityType = TranslationEntityType.Status,
                    entityKeys = listOf(statusKey),
                    targetLanguage = "ja",
                )

            assertEquals(1, zh.size)
            assertEquals(1, ja.size)
            assertEquals(TranslationStatus.Completed, zh.single().status)
            assertEquals(TranslationStatus.Skipped, ja.single().status)
            assertEquals("same_language", ja.single().statusReason)
        }

    @Test
    fun updateReplacesProfileTranslationStateAndPayload() =
        runTest {
            val userKey = MicroBlogKey("user-1", "test.social")
            val entityKey = profileTranslationEntityKey(userKey)

            db.translationDao().insert(
                DbTranslation(
                    entityType = TranslationEntityType.Profile,
                    entityKey = entityKey,
                    targetLanguage = "en",
                    sourceHash = "hash-old",
                    status = TranslationStatus.Pending,
                    updatedAt = 1L,
                ),
            )

            db.translationDao().update(
                entityType = TranslationEntityType.Profile,
                entityKey = entityKey,
                targetLanguage = "en",
                sourceHash = "hash-new",
                status = TranslationStatus.Completed,
                payload = TranslationPayload(description = "Translated profile".toUiPlainText()),
                statusReason = null,
                attemptCount = 2,
                updatedAt = 99L,
            )

            val saved =
                db.translationDao().get(
                    entityType = TranslationEntityType.Profile,
                    entityKey = entityKey,
                    targetLanguage = "en",
                )

            assertNotNull(saved)
            assertEquals("hash-new", saved.sourceHash)
            assertEquals(2, saved.attemptCount)
            assertEquals(99L, saved.updatedAt)
            assertEquals("Translated profile", saved.payload?.description?.raw)
        }

    @Test
    fun deleteByLanguage_removesOnlyMatchingRows() =
        runTest {
            db.translationDao().insertAll(
                listOf(
                    DbTranslation(
                        entityType = TranslationEntityType.Status,
                        entityKey = "status:a",
                        targetLanguage = "zh-CN",
                        sourceHash = "hash-a",
                        status = TranslationStatus.Completed,
                        payload = TranslationPayload(content = "A".toUiPlainText()),
                        updatedAt = 1L,
                    ),
                    DbTranslation(
                        entityType = TranslationEntityType.Profile,
                        entityKey = "profile:b",
                        targetLanguage = "ja",
                        sourceHash = "hash-b",
                        status = TranslationStatus.Completed,
                        payload = TranslationPayload(description = "B".toUiPlainText()),
                        updatedAt = 1L,
                    ),
                ),
            )

            db.translationDao().deleteByLanguage("zh-CN")

            val removed = db.translationDao().get(TranslationEntityType.Status, "status:a", "zh-CN")
            val kept = db.translationDao().get(TranslationEntityType.Profile, "profile:b", "ja")

            assertNull(removed)
            assertNotNull(kept)
            assertEquals("B", kept.payload?.description?.raw)
        }
}
