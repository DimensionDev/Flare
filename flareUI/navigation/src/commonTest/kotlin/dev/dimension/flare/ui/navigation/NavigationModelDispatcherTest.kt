@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    ExperimentalFlareNavigation::class,
)

package dev.dimension.flare.ui.navigation

import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareSubcomposition
import dev.dimension.flare.ui.FlareSubcompositionFactory
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

private object UnusedDispatcherSubcompositionFactory : FlareSubcompositionFactory {
    override fun create(root: FlareChildren): FlareSubcomposition = error("Dispatcher tests do not compose entries.")
}
