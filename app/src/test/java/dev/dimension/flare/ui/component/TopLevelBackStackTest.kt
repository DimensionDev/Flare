package dev.dimension.flare.ui.component

import dev.dimension.flare.ui.route.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelBackStackTest {
    @Test
    fun secondaryRouteKeepsItsOwnTopLevelStack() {
        val backStack = TopLevelBackStack<Route>(Route.Home)

        backStack.addTopLevel(Route.Settings.Main)
        backStack.add(Route.Settings.AppearanceLayout)
        backStack.addTopLevel(Route.DraftBox)
        backStack.addTopLevel(Route.Settings.Main)

        assertEquals(Route.Settings.Main, backStack.topLevelKey)
        assertEquals(Route.Settings.AppearanceLayout, backStack.currentKey)
    }
}
