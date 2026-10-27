package dev.dimension.flare.ui.component

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShareImageLoadingTest {
    @Test
    fun exportWaitsForEveryMountedImage() =
        runTest {
            val loading = ShareImageLoading()
            loading.register("avatar")
            loading.register("photo")
            val export = async { loading.awaitReady() }
            runCurrent()
            loading.update("avatar", true)
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertFalse(export.isCompleted)
            loading.update("photo", true)
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertTrue(export.isCompleted)
            export.await()
        }

    @Test
    fun failedMediaBlocksExportAndRemovedMediaDoesNotBlockNextImage() =
        runTest {
            val loading = ShareImageLoading()
            loading.update("photo", false)
            assertFailsWith<IllegalStateException> { loading.awaitReady() }
            loading.remove("photo")
            loading.update("photo", false)
            loading.awaitReady()
        }

    @Test
    fun timedOutMediaIsAnExportFailureRatherThanCancellingTheAction() =
        runTest {
            val loading = ShareImageLoading()
            loading.register("stalled")
            assertFailsWith<IllegalStateException> { loading.awaitReady() }
        }

    @Test
    fun alreadyCompletedImageIsNotResetWhenItsEffectRegisters() =
        runTest {
            val loading = ShareImageLoading()
            loading.update("cached", true)
            loading.register("cached")
            loading.awaitReady()
        }
}
