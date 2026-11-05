package dev.dimension.flare.common

import dev.dimension.flare.rust.PresenterException
import dev.dimension.flare.rust.UiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RustPresenterTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initializationFailuresAreReportedAndReleaseEveryCreatedResource() {
        for (stage in listOf("factory", "subscribe", "snapshot")) {
            val native = Handle()
            val subscription = Handle()
            val binding = binding(native, subscription, stage)
            val owner =
                RustPresenter(binding) {
                    check(stage != "factory") { stage }
                    native
                }
            assertNull(owner.state.value)
            assertEquals(UiError("native_error", stage), owner.error.value)
            owner.perform { error("failed initialization must disable actions") }
            owner.close()
            owner.close()
            assertEquals(if (stage == "factory") 0 else 1, native.closed)
            assertEquals(native.closed, native.stopped)
            assertEquals(if (stage == "snapshot") 1 else 0, subscription.closed)
            assertEquals(subscription.closed, subscription.stopped)
        }
    }

    @Test
    fun successfulInitializationKeepsResourcesUntilClosed() {
        val native = Handle()
        val subscription = Handle()
        val owner = RustPresenter(binding(native, subscription)) { native }
        assertEquals(17, owner.state.value)
        assertNull(owner.error.value)
        assertEquals(0, native.closed)
        assertEquals(0, subscription.closed)
        owner.close()
        owner.close()
        assertEquals(1, native.closed)
        assertEquals(1, subscription.closed)
    }

    @Test
    fun initializationCancellationStillPropagates() {
        assertThrows(CancellationException::class.java) {
            RustPresenter(binding(Handle(), Handle())) { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun actionErrorsKeepDataAndTheNextActionClearsTheError() {
        val native = Handle()
        val owner = RustPresenter(binding(native, Handle())) { native }
        val error = UiError("counter_overflow", "Counter overflow")
        owner.perform { throw PresenterException.Failed(error) }
        dispatcher.scheduler.runCurrent()
        assertEquals(17, owner.state.value)
        assertEquals(error, owner.error.value)
        owner.perform { }
        dispatcher.scheduler.runCurrent()
        assertEquals(17, owner.state.value)
        assertNull(owner.error.value)
        owner.perform { throw CancellationException("cancelled") }
        dispatcher.scheduler.runCurrent()
        assertNull(owner.error.value)
        owner.close()
    }

    private fun binding(
        native: Handle,
        subscription: Handle,
        failure: String? = null,
    ) = RustPresenterBinding<Handle, Int, Handle>(
        key = "test",
        snapshot = {
            check(failure != "snapshot") { "snapshot" }
            17
        },
        subscribe = {
            check(failure != "subscribe") { "subscribe" }
            subscription
        },
        nextState = { awaitCancellation() },
        stop = { it.stopped++ },
        shutdown = {
            assertEquals(native, it)
            it.stopped++
        },
    )

    private class Handle : AutoCloseable {
        var stopped = 0
        var closed = 0

        override fun close() {
            closed++
        }
    }
}
