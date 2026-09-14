@file:OptIn(
    dev.dimension.compose.nativekit.LowLevelNativeKitApi::class,
    ExperimentalNativeKitNavigation::class,
)

package dev.dimension.compose.nativekit.navigation

import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitSubcomposition
import dev.dimension.compose.nativekit.NativeKitSubcompositionFactory
import kotlin.test.Test
import kotlin.test.assertEquals

public class NavigationModelDispatcherTest {
    @Test
    public fun stopsDeliveringModelsAfterTheObserverIsRemoved() {
        val models = mutableListOf<NavigationModel>()
        val dispatcher = NavigationModelDispatcher()
        val stop = dispatcher.observe(models::add)
        val delivered = unusedModel()
        dispatcher.dispatch(delivered)

        stop()
        dispatcher.dispatch(unusedModel())

        assertEquals(listOf(delivered), models)
    }
}

private fun unusedModel(): NavigationModel =
    NavigationModel(
        entries = emptyList(),
        onBack = {},
        subcompositions = UnusedDispatcherSubcompositionFactory,
    )

private object UnusedDispatcherSubcompositionFactory : NativeKitSubcompositionFactory {
    override fun create(root: NativeKitChildren): NativeKitSubcomposition = error("Dispatcher tests do not compose entries.")
}
