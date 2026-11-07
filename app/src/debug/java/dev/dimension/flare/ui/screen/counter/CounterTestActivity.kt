package dev.dimension.flare.ui.screen.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import dev.dimension.flare.ui.route.AndroidNavDisplay
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.androidCloseTransition
import dev.dimension.flare.ui.route.androidOpenTransition

/** Recreates the real screen and navigation owners on Activity recreation. */
class CounterTestActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeApi::class, ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val stack = rememberNavBackStack(Route.Home)
                AndroidNavDisplay(
                    cornerShape = null,
                    transitionSpec = { androidOpenTransition(96) },
                    popTransitionSpec = { androidCloseTransition(96) },
                    entries =
                        rememberDecoratedNavEntries(
                            backStack = stack,
                            entryDecorators =
                                listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                ),
                            entryProvider =
                                entryProvider<NavKey> {
                                    entry<Route.Home> {
                                        Button(onClick = { stack.add(Route.Counter()) }, modifier = Modifier.testTag("compose")) {
                                            Text("Compose")
                                        }
                                    }
                                    entry<Route.Counter> {
                                        Box {
                                            CounterScreen(
                                                onBack = { stack.removeLastOrNull() },
                                                initialValue = intent.getLongExtra("counter-initial-value", 0),
                                            )
                                            Button(
                                                onClick = { stack.add(Route.Counter()) },
                                                modifier = Modifier.align(Alignment.BottomCenter).testTag("counter-next"),
                                            ) {
                                                Text("Next")
                                            }
                                        }
                                    }
                                },
                        ),
                    sceneStrategies = remember { listOf(SinglePaneSceneStrategy()) },
                    onBack = { stack.removeLastOrNull() },
                )
            }
        }
    }
}
