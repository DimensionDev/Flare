package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StatusReactionRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feedReactionsCanShrinkAndBecomeEmpty() {
        assertReactionsCanShrink(isDetail = false)
    }

    @Test
    fun detailReactionsCanShrinkAndBecomeEmpty() {
        assertReactionsCanShrink(isDetail = true)
    }

    private fun assertReactionsCanShrink(isDetail: Boolean) {
        var reactions by mutableStateOf(persistentListOf("first", "second"))
        val instances = mutableMapOf<String, Any>()
        composeRule.setContent {
            Box(Modifier.size(240.dp, 400.dp)) {
                Reactions(reactions, isDetail, instances)
            }
        }
        composeRule.onNodeWithTag("second").assertIsDisplayed()
        val retained = composeRule.runOnIdle { instances.getValue("first") }

        composeRule.runOnIdle { reactions = persistentListOf("first") }
        composeRule.onNodeWithTag("first").assertIsDisplayed()
        composeRule.onNodeWithTag("second").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(setOf("first"), instances.keys)
            assertSame(retained, instances.getValue("first"))
        }

        composeRule.runOnIdle { reactions = persistentListOf() }
        composeRule.onNodeWithTag("first").assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(instances.isEmpty()) }

        composeRule.runOnIdle { reactions = persistentListOf("third", "fourth") }
        composeRule.onNodeWithTag("third").assertIsDisplayed()
        composeRule.onNodeWithTag("fourth").assertIsDisplayed()
    }

    @Composable
    private fun Reactions(
        reactions: ImmutableList<String>,
        isDetail: Boolean,
        instances: MutableMap<String, Any>,
    ) {
        StatusReactionRow(itemCount = reactions.size, isDetail = isDetail) { index ->
            val reaction = reactions[index]
            val instance = remember { Any() }
            DisposableEffect(reaction) {
                instances[reaction] = instance
                onDispose { instances.remove(reaction) }
            }
            Box(Modifier.size(40.dp).testTag(reaction))
        }
    }
}
