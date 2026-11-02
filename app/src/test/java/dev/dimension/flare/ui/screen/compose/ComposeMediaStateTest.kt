package dev.dimension.flare.ui.screen.compose

import android.net.Uri
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ComposeMediaStateTest {
    @Test
    fun addingTooManyImagesKeepsExistingAttachmentsAndAltText() =
        runTest {
            val state =
                ComposeMediaState(RuntimeEnvironment.getApplication(), {
                    ComposeConfig.Media(maxCount = 2, canSensitive = true, altTextMaxLength = 500, allowMediaOnly = true)
                }, scope = this)
            val first = Uri.parse("content://images/first.png")
            val second = Uri.parse("content://images/second.png")
            state.addMedia(listOf(first))
            state.medias
                .single()
                .textState
                .edit { append("Existing description") }

            state.addMedia(listOf(second, Uri.parse("content://images/third.png")))

            assertEquals(listOf(first, second), state.medias.map { it.uri })
            assertEquals(
                "Existing description",
                state.medias
                    .first()
                    .textState.text
                    .toString(),
            )
        }

    @Test
    fun importsReserveCapacityAndSubmissionWaitsWithoutDuplicating() =
        runTest {
            val ready = CompletableDeferred<Unit>()
            val imported = File.createTempFile("compose", ".gif")
            val state =
                ComposeMediaState(
                    RuntimeEnvironment.getApplication(),
                    { config(2) },
                    scope = this,
                    importImage = {
                        ready.await()
                        imported
                    },
                )
            state.importImages(listOf(Uri.parse("content://images/a.gif")))
            state.addMedia(listOf(Uri.parse("content://images/b.png"), Uri.parse("content://images/c.png")))
            var dispatches = 0
            var finish: ((Boolean) -> Unit)? = null
            val dispatch: ((Boolean) -> Unit) -> Unit = {
                dispatches++
                finish = it
            }
            state.dispatchWhenReady(dispatch) {}
            state.dispatchWhenReady(dispatch) {}
            runCurrent()
            assertEquals(0, dispatches)
            ready.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, dispatches)
            assertEquals(listOf(Uri.fromFile(imported), Uri.parse("content://images/b.png")), state.medias.map { it.uri })
            state.close()
            assertTrue(imported.exists())
            finish!!(true)
            assertFalse(imported.exists())
        }

    @Test
    fun failureBlocksSubmissionAndACompletedRetryCanRecover() =
        runTest {
            var fails = true
            var errors = 0
            var dispatches = 0
            val imported = File.createTempFile("compose", ".png")
            val state =
                ComposeMediaState(
                    RuntimeEnvironment.getApplication(),
                    { config(2) },
                    scope = this,
                    onError = { errors++ },
                    importImage = { if (fails) error("Unreadable image") else imported },
                )
            state.importImages(listOf(Uri.parse("content://images/a.png")))
            state.dispatchWhenReady({
                dispatches++
                it(true)
            }) {}
            advanceUntilIdle()
            assertEquals(1, errors)
            assertEquals(0, dispatches)
            fails = false
            state.importImages(listOf(Uri.parse("content://images/a.png")))
            state.dispatchWhenReady({
                dispatches++
                it(true)
            }) {}
            advanceUntilIdle()
            assertEquals(1, dispatches)
            state.close()
        }

    @Test
    fun completionRechecksPollAndAccountCapacityAndCloseCancels() =
        runTest {
            val ready = CompletableDeferred<Unit>()
            var allowed = true
            var max = 2
            val imported = File.createTempFile("compose", ".png")
            val state =
                ComposeMediaState(
                    RuntimeEnvironment.getApplication(),
                    { config(max) },
                    scope = this,
                    imagesAllowed = { allowed },
                    importImage = {
                        ready.await()
                        imported
                    },
                )
            state.addMedia(listOf(Uri.parse("content://images/old.png")))
            state.importImages(listOf(Uri.parse("content://images/a.png")))
            runCurrent()
            max = 1
            ready.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, state.medias.size)
            assertFalse(imported.exists())
            allowed = false
            max = 4
            state.importImages(listOf(Uri.parse("content://images/b.png")))
            assertFalse(state.hasPendingImports)
            state.close()
            assertFalse(state.hasPendingImports)
        }

    @Test
    fun replacingDraftCancelsPendingSubmissionAndLateImport() =
        runTest {
            val ready = CompletableDeferred<Unit>()
            val state =
                ComposeMediaState(
                    RuntimeEnvironment.getApplication(),
                    { config(4) },
                    scope = this,
                    importImage = {
                        ready.await()
                        error("Cancelled source must not be read")
                    },
                )
            state.importImages(listOf(Uri.parse("content://images/a.png")))
            var dispatched = false
            state.dispatchWhenReady({ dispatched = true }) {}
            runCurrent()
            state.replaceMedias(emptyList())
            ready.complete(Unit)
            advanceUntilIdle()
            assertFalse(dispatched)
            assertTrue(state.medias.isEmpty())
            assertFalse(state.hasPendingImports)
        }

    @Test
    fun oldDraftCompletionDoesNotDismissOrUnlockANewSubmission() =
        runTest {
            val state = ComposeMediaState(RuntimeEnvironment.getApplication(), { config(4) }, scope = this)
            var first: ((Boolean) -> Unit)? = null
            var second: ((Boolean) -> Unit)? = null
            var dismissals = 0
            state.dispatchWhenReady({ first = it }) { dismissals++ }
            runCurrent()
            state.replaceMedias(emptyList())
            state.dispatchWhenReady({ second = it }) { dismissals++ }
            runCurrent()
            first!!(true)
            state.dispatchWhenReady({ error("A submission is already preparing") }) {}
            runCurrent()
            assertEquals(0, dismissals)
            second!!(true)
            assertEquals(1, dismissals)
        }

    private fun config(max: Int) = ComposeConfig.Media(maxCount = max, canSensitive = true, altTextMaxLength = 500, allowMediaOnly = true)
}
