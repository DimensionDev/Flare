package dev.dimension.flare.common

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import dev.dimension.flare.ui.component.Media3VideoCacheManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AndroidDownloadManagerTest {
    private lateinit var provider: SaveProvider
    private lateinit var manager: AndroidDownloadManager
    private val bytes = byteArrayOf(1, 2, 3)

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        provider = SaveProvider(context)
        provider.attachInfo(context, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)
        manager =
            AndroidDownloadManager(
                context = context,
                media3VideoCacheManager = Media3VideoCacheManager(context),
                mediaSaveLocationRepository = AndroidMediaSaveLocationRepository(context),
                mediaSavePicker = AndroidMediaSavePicker(),
            )
    }

    @After
    fun tearDown() {
        provider.file.delete()
    }

    @Test
    fun publishesWrittenBytesWithoutDeletingTheOutput() {
        assertTrue(save())
        assertArrayEquals(bytes, provider.file.readBytes())
        assertEquals(listOf("insert", "write", "publish"), provider.operations)
        assertTrue(provider.deletedUris.isEmpty())
    }

    @Test
    fun returnsFailureAndDeletesOnlyTheNewUriWhenPublishingConflicts() {
        provider.publishFailure = SQLiteConstraintException("UNIQUE constraint failed: files._data")

        assertFalse(save())
        assertEquals(listOf("insert", "write", "publish", "delete"), provider.operations)
        assertEquals(listOf(provider.outputUri), provider.deletedUris)
    }

    @Test
    fun doesNotReportSuccessWhenPublishingUpdatesNoRows() {
        provider.updatedRows = 0

        assertFalse(save())
        assertEquals(listOf(provider.outputUri), provider.deletedUris)
    }

    @Test
    fun returnsFailureWhenInsertionFailsWithoutDeletingAnotherUri() {
        provider.insertFailure = SQLiteConstraintException("UNIQUE constraint failed: files._data")

        assertFalse(save())
        assertEquals(listOf("insert"), provider.operations)
        assertTrue(provider.deletedUris.isEmpty())
    }

    @Test
    fun returnsFailureWhenInsertionReturnsNoUri() {
        provider.insertReturnsNull = true

        assertFalse(save())
        assertEquals(listOf("insert"), provider.operations)
        assertTrue(provider.deletedUris.isEmpty())
    }

    @Test
    fun cleanupFailureDoesNotEscapeAfterAWriteFailure() {
        provider.writeFailure = IOException("Write failed")
        provider.deleteFailure = SecurityException("Delete failed")

        assertFalse(save())
        assertEquals(listOf("insert", "write", "delete"), provider.operations)
        assertEquals(listOf(provider.outputUri), provider.deletedUris)
    }

    @Test
    fun cleanupFailureDoesNotEscapeAfterAPublishFailure() {
        provider.publishFailure = SQLiteConstraintException("UNIQUE constraint failed: files._data")
        provider.deleteFailure = SecurityException("Delete failed")

        assertFalse(save())
        assertEquals(listOf(provider.outputUri), provider.deletedUris)
    }

    @Test
    fun cancellationIsRethrownAfterCleanupEvenWhenCleanupFails() {
        val cancellation = CancellationException("Save canceled")
        provider.writeFailure = cancellation
        provider.deleteFailure = SecurityException("Delete failed")

        assertEquals(cancellation.message, assertThrows(CancellationException::class.java) { save() }.message)
        assertEquals(listOf(provider.outputUri), provider.deletedUris)
    }

    private fun save(): Boolean =
        runBlocking {
            manager.saveByteArray(bytes, "media.png", "image/png")
        }

    private class SaveProvider(
        context: Context,
    ) : ContentProvider() {
        val file = File.createTempFile("media-save-test-", ".png", context.cacheDir)
        val outputUri: Uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "42")
        val operations = mutableListOf<String>()
        val deletedUris = mutableListOf<Uri>()
        var insertFailure: Exception? = null
        var writeFailure: Exception? = null
        var publishFailure: Exception? = null
        var deleteFailure: Exception? = null
        var insertReturnsNull = false
        var updatedRows = 1

        override fun onCreate(): Boolean = true

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? {
            operations += "insert"
            assertEquals(MediaStore.Downloads.EXTERNAL_CONTENT_URI, uri)
            assertEquals(1, values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            insertFailure?.let { throw it }
            return outputUri.takeUnless { insertReturnsNull }
        }

        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor {
            operations += "write"
            assertEquals(outputUri, uri)
            writeFailure?.let { throw it }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            operations += "publish"
            assertEquals(outputUri, uri)
            assertEquals(0, values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            publishFailure?.let { throw it }
            return updatedRows
        }

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            operations += "delete"
            deletedUris += uri
            deleteFailure?.let { throw it }
            return 1
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String = "image/png"
    }
}
