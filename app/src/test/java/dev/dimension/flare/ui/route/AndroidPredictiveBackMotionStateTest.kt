package dev.dimension.flare.ui.route

import androidx.navigationevent.NavigationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPredictiveBackMotionStateTest {
    @Test
    fun zeroProgressGestureRemainsOwnedAtCommit() {
        val scope = CoroutineScope(Job())
        val state = AndroidPredictiveBackMotionState(coroutineScope = scope)
        var popAttempted = false

        state.onProgress(
            outgoingSceneKey = "outgoing",
            incomingSceneKey = "incoming",
            event =
                NavigationEvent(
                    swipeEdge = NavigationEvent.EDGE_LEFT,
                    progress = 0f,
                ),
        )

        assertTrue(
            state.commit {
                popAttempted = true
                false
            },
        )
        assertTrue(popAttempted)
        scope.cancel()
    }
}
