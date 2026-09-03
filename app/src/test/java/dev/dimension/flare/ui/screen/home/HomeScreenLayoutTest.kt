package dev.dimension.flare.ui.screen.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenLayoutTest {
    @Test
    fun fixedSecondarySidebarIsOnlyUsedByWideSingleColumnLayouts() {
        assertFalse(usesFixedSecondarySidebar(singleColumn = false, availableWidth = 1200.dp))
        assertFalse(usesFixedSecondarySidebar(singleColumn = true, availableWidth = 1023.dp))
        assertTrue(usesFixedSecondarySidebar(singleColumn = true, availableWidth = 1024.dp))
    }
}
