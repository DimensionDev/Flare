package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.FileSystemUtilsExt
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.repository.PluginAuthoring
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.di.DesktopKoinApplication
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.github.GitHubPluginRepositoryV1
import dev.dimension.flare.feature.plugin.github.GitHubPluginVersionV1
import dev.dimension.flare.feature.plugin.management.PluginManagementItemV1
import dev.dimension.flare.ui.component.LocalExternalTextResolver
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.LocalComposeWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.startKoin
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class DesktopPluginManagementScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var root: Path
    private lateinit var originalHome: String
    private lateinit var subsystem: PluginSubsystemV1
    private var visible by mutableStateOf(true)

    @Before
    fun setUp() {
        originalHome = System.getProperty("user.home")
        root = Files.createTempDirectory("flare-plugin-screen-test")
        System.setProperty("user.home", root.toString())
        assertEquals(root.resolve(".flare").toFile(), FileSystemUtilsExt.flareDirectory())
        val application = startKoin<DesktopKoinApplication>()
        subsystem = application.koin.get()
    }

    @After
    fun tearDown() {
        visible = false
        if (::subsystem.isInitialized) {
            runBlocking { subsystem.runtimePool.close() }
            subsystem.github.close()
        }
        stopKoin()
        System.setProperty("user.home", originalHome)
        root.toFile().deleteRecursively()
    }

    @Test
    fun createWithAiOpensTheGeneralAssistantWithoutCreatingAPlugin() {
        var opens = 0
        compose.setContent {
            if (visible) {
                CompositionLocalProvider(
                    LocalComposeWindow provides null,
                    LocalTimelineAppearance provides TimelineAppearance(aiConfig = TimelineAppearance.AiConfig(agent = true)),
                ) {
                    FlareTheme(isDarkTheme = false) {
                        Box(Modifier.width(448.dp).height(800.dp)) {
                            DesktopPluginManagementScreen(onBack = {}, onCreateWithAI = { opens++ })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Create with Flare AI").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add plugin").performClick()
        compose.onNodeWithText("Create with Flare AI").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Add plugin").performClick()
        compose.onNodeWithText("Create with Flare AI").performClick()
        compose.runOnIdle {
            assertEquals(2, opens)
            assertEquals(emptyList<dev.dimension.flare.data.repository.AuthoredPlugin>(), koinGet<PluginAuthoring>().plugins.value)
        }
    }

    @Test
    fun generatedPluginAndAddMenuScreenshots() {
        runBlocking { createScreenshotPlugin(koinGet<PluginAuthoring>()) }
        compose.setContent {
            if (visible) {
                CompositionLocalProvider(
                    LocalComposeWindow provides null,
                    LocalTimelineAppearance provides TimelineAppearance(aiConfig = TimelineAppearance.AiConfig(agent = true)),
                ) {
                    FlareTheme(isDarkTheme = false) {
                        Box(Modifier.width(520.dp).height(660.dp)) {
                            DesktopPluginManagementScreen(onBack = {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Pixelfed").assertIsDisplayed()
        compose.onNodeWithText("Enabled").assertDoesNotExist()
        ImageIO.write(
            compose.onRoot().captureToImage().toAwtImage(),
            "png",
            File(System.getProperty("java.io.tmpdir"), "flare-plugins-desktop.png"),
        )
        compose.onNodeWithContentDescription("Add plugin").performClick()
        compose.onNodeWithText("Create with Flare AI").assertIsDisplayed()
        ImageIO.write(
            compose.onNode(isPopup()).captureToImage().toAwtImage(),
            "png",
            File(System.getProperty("java.io.tmpdir"), "flare-plugins-desktop-menu.png"),
        )
    }

    @Test
    fun developmentValidatesWithoutReloadingUntilTheUserClicks() {
        var state by mutableStateOf(
            dev.dimension.flare.feature.plugin.development.PluginDevelopmentStateV1(
                directory = "/tmp/plugin",
                active = true,
                pluginId = "fixture.plugin",
                candidateHash = "new",
                runningHash = "old",
            ),
        )
        var reloads = 0
        compose.setContent {
            FlareTheme(isDarkTheme = false) {
                PluginDevelopmentPanel(state, onOpen = {}, onReload = { reloads++ }, onCancel = {}, onStop = {})
            }
        }
        compose.onNodeWithText("Validated · ready to load").assertIsDisplayed()
        assertEquals(0, reloads)
        compose.onNodeWithText("Reload plugin").performClick()
        assertEquals(1, reloads)
        compose.runOnIdle { state = state.copy(validating = true, candidateHash = null) }
        compose.onNodeWithText("Reload plugin").assertIsNotEnabled()
        compose.onNodeWithText("Choose folder…").assertIsNotEnabled()
    }

    @Test
    fun installReviewDisclosesUnrestrictedMediaNetworking() {
        compose.setContent {
            if (visible) {
                FlareTheme(isDarkTheme = false) {
                    PluginInstallReviewDialog(
                        review =
                            dev.dimension.flare.feature.plugin.management.PluginInstallReviewV1(
                                pluginId = "fixture.plugin",
                                platformId = "Fixture",
                                name = "Fixture",
                                version = "1.0.0",
                                existingVersion = null,
                                capabilities = emptyList(),
                                warnings = emptyList(),
                                sourceUrl = null,
                                permissions =
                                    listOf(
                                        dev.dimension.flare.data.model.PluginPermission(
                                            dev.dimension.flare.data.model.PluginPermissionType.MediaNetwork,
                                            "*",
                                        ),
                                    ),
                            ),
                        busy = false,
                        onConfirm = {},
                        onCancel = {},
                    )
                }
            }
        }
        compose.onNodeWithText("• Load media from any website, which may receive this plugin’s account data").assertIsDisplayed()
    }

    @Test
    fun localizedPluginNameAndActionsRemainVisibleWithLongRepositoryUrl() {
        val repository = GitHubPluginRepositoryV1(1, "Tlaster", "flare-plugin-github-e2e-20261028")
        val plugin =
            PluginManagementItemV1(
                pluginId = "fixture.plugin",
                platformId = "Fixture",
                nameText = UiText.ExternalRef("fixture.plugin", "name", "Fixture plugin"),
                version = "1.0.0",
                enabled = true,
                running = true,
                pendingRestart = false,
                iconPath = root.resolve("missing-icon.png").toString(),
                sourceUrl = repository.url,
                availableUpdate = GitHubPluginVersionV1("v1.1.0", "1.1.0", "b".repeat(40)),
            )
        var removed: String? = null
        var updated: String? = null
        compose.setContent {
            if (visible) {
                FlareTheme(isDarkTheme = false) {
                    CompositionLocalProvider(LocalExternalTextResolver provides { "Localized plugin" }) {
                        Box(Modifier.width(448.dp).height(800.dp).clipToBounds()) {
                            PluginItem(
                                plugin = plugin,
                                busy = false,
                                onUninstall = { removed = it },
                                onUpdate = { updated = it },
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Localized plugin").assertIsDisplayed()
        compose.onNodeWithContentDescription("Localized plugin").assertExists()
        compose.onNodeWithText("Update v1.1.0").assertIsDisplayed().performClick()
        assertEquals(plugin.pluginId, updated)
        compose.onNodeWithContentDescription("Uninstall").assertIsDisplayed().performClick()
        assertEquals(plugin.pluginId, removed)
    }

    @Test
    fun narrowWindowGroupsInstallSourcesAndHidesAiWhenDisabled() {
        compose.setContent {
            if (visible) {
                CompositionLocalProvider(LocalComposeWindow provides null, LocalTimelineAppearance provides TimelineAppearance.Default) {
                    FlareTheme(isDarkTheme = false) {
                        // A 520 dp app window leaves 448 dp after its navigation rail.
                        Box(Modifier.width(448.dp).height(800.dp)) {
                            DesktopPluginManagementScreen(onBack = {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("From file…").assertDoesNotExist()
        compose.onNodeWithText("Install from GitHub").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add plugin").performClick()
        compose.onNodeWithText("From file…").assertIsDisplayed()
        compose.onNodeWithText("Create with Flare AI").assertDoesNotExist()
        compose.onNodeWithText("Install from GitHub").assertIsDisplayed().performClick()
        compose.onNodeWithText("GitHub repository URL").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("GitHub repository URL").assertDoesNotExist()
    }
}

private suspend fun createScreenshotPlugin(authoring: PluginAuthoring) {
    val plugin = authoring.create("plugin-management-ui-fixture")
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
