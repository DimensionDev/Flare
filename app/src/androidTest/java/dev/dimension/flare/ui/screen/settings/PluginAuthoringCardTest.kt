package dev.dimension.flare.ui.screen.settings

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.R
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoring
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.segmentedShapes2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
class PluginAuthoringCardTest {
    @get:Rule val compose = createComposeRule()
    private val mediaQueryIntegration = ComposeUiFlags.isMediaQueryIntegrationEnabled
    private var screenshotPluginId: String? = null

    @Before fun setUp() {
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
    }

    @After fun tearDown() {
        try {
            screenshotPluginId?.let { id -> runBlocking { koinGet<PluginAuthoring>().delete(id) } }
        } finally {
            ComposeUiFlags.isMediaQueryIntegrationEnabled = mediaQueryIntegration
        }
    }

    @Test
    fun generatedPluginAndAddMenuScreenshots() {
        runBlocking {
            val authoring = koinGet<PluginAuthoring>()
            val plugin = authoring.create("plugin-management-ui-fixture")
            screenshotPluginId = plugin.id
            writeScreenshotPlugin(authoring, plugin)
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalGlobalAppearance provides GlobalAppearance.Default,
                LocalTimelineAppearance provides TimelineAppearance(aiConfig = TimelineAppearance.AiConfig(agent = true)),
            ) {
                FlareTheme(darkTheme = false, dynamicColor = false) {
                    AndroidPluginManagementScreen(onBack = {})
                }
            }
        }
        compose.onNodeWithText("Pixelfed").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        screenshot("flare-plugins-android.png")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(context.getString(R.string.plugin_add)).performClick()
        compose.onNodeWithText(context.getString(R.string.plugin_ai_create)).assertIsDisplayed()
        screenshot("flare-plugins-android-menu.png")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.externalCacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun pendingActionsFitNarrowCard() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    SegmentedListItem(
                        onClick = {},
                        shapes = ListItemDefaults.segmentedShapes2(0, 1),
                        content = {
                            PluginAuthoringCard(
                                AuthoredPlugin(
                                    "local.ai.example",
                                    "Pixelfed",
                                    phase = PluginAuthoringPhase.WaitingForOperations,
                                    revision = 1,
                                ),
                                busy = false,
                                onCancelUpdate = {},
                                onUninstall = {},
                            )
                        },
                    )
                }
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        compose.onNodeWithText("Allow and continue").assertDoesNotExist()
        val layouts = mutableListOf<TextLayoutResult>()
        val cancelLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.plugin_ai_stop)
        compose
            .onNodeWithText(cancelLabel, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Cancel update must remain readable", layouts.single().lineCount <= 2)
    }
}

private suspend fun writeScreenshotPlugin(
    authoring: PluginAuthoring,
    plugin: AuthoredPlugin,
) {
    val current = authoring.read(plugin.id)
    authoring.write(
        plugin.id,
        current.plugin.revision,
        mapOf(
            "manifest.json" to """{
            "schemaVersion":1,"apiVersion":1,"id":"${plugin.id}","version":"1.0.0",
            "defaultLocale":"en","name":"Pixelfed",
            "platform":{"id":"${plugin.platformId}","name":"Pixelfed","detector":{"priority":10},
                "loginMethods":[{"id":"oauth","interaction":"OAuth","title":"OAuth"}],
                "capabilities":{"flare.datasource.timeline/v1":{"operations":{"page":{"directions":["refresh","older"]}}}},
                "timelines":[{"id":"home","title":"Home","defaultForNewAccount":true}]}
        }""",
            "plugin.js" to """definePlugin({
            detector:{async detect(){return {matched:true};}},
            login:{oauth:{async begin(){return {type:"Pending"};},async resume(){return {type:"Pending"};}}},
            capabilities:{timeline:{async page(){return {items:[]};}}}
        });""",
        ),
    )
    withTimeout(10_000) {
        authoring.plugins.first { plugins ->
            plugins.any { it.id == plugin.id && it.phase == PluginAuthoringPhase.Applied }
        }
    }
}
