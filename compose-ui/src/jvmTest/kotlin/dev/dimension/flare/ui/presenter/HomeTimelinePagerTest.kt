package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import moe.tlaster.precompose.molecule.producePresenter
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class HomeTimelinePagerTest {
    @get:Rule val composeRule = createComposeRule()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    @After fun close() = owner.viewModelStore.clear()

    @Test fun reorderingAndRemovingTabsKeepTheDisplayedSelection() {
        var tabs by mutableStateOf(listOf("A", "B", "C"))
        lateinit var state: PagerState
        var selected = "B"
        val selections = mutableListOf<String>()
        var presentedTabs = listOf("A", "B", "C")
        composeRule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                val model by producePresenter("home-tabs-test") {
                    val currentTabs = tabs
                    val pager =
                        rememberHomeTimelinePagerState(currentTabs, "B") {
                            selected = it
                            selections += it
                        }
                    currentTabs to pager
                }
                val currentTabs = model.first
                val pager = model.second
                SideEffect {
                    state = pager
                    presentedTabs = currentTabs
                }
                HorizontalPager(state = pager, key = { currentTabs[it] }, modifier = Modifier.size(350.dp, 420.dp).testTag("tabs")) {
                    Box(Modifier.size(350.dp, 420.dp))
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals("B", selected)
            tabs = listOf("B", "A", "C")
        }
        composeRule.waitUntil(5_000) { presentedTabs == listOf("B", "A", "C") }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("B", selected)
            assertEquals(0, state.currentPage)
            assertEquals(listOf("B"), selections, "Reorder must not save a transient wrong tab")
            tabs = listOf("A", "C")
        }
        composeRule.waitUntil(5_000) { presentedTabs == listOf("A", "C") }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("A", selected, "Removing the selected tab must persist the displayed replacement")
            assertEquals(0, state.currentPage)
        }
        composeRule.onNodeWithTag("tabs").performTouchInput {
            down(center)
            moveBy(Offset(-400f, 0f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tabs").performTouchInput {
            moveBy(Offset(400f, 0f))
            advanceEventTime(500)
            up()
        }
        composeRule.runOnIdle {
            assertEquals("A", selected)
            assertEquals(listOf("B", "A"), selections, "Returning an unsettled swipe must not save the same tab again")
        }
        composeRule.onNodeWithTag("tabs").performTouchInput { swipeLeft() }
        composeRule.runOnIdle {
            assertEquals("C", selected)
            assertEquals(listOf("B", "A", "C"), selections, "Only settled tab changes should be persisted")
        }
    }
}
