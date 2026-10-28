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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.FileSystemUtilsExt
import dev.dimension.flare.data.repository.PluginAuthoring
import dev.dimension.flare.di.DesktopKoinApplication
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.github.GitHubPluginRepositoryV1
import dev.dimension.flare.feature.plugin.github.GitHubPluginVersionV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginInstallSourceV1
import dev.dimension.flare.feature.plugin.management.PluginManagementItemV1
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.LocalComposeWindow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.startKoin
import java.nio.file.Files
import java.nio.file.Path

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
    fun creatingAnAiPluginImmediatelySavesItAndOpensItsOwnConversation() {
        var opened: String? = null
        compose.setContent {
            if (visible) {
                CompositionLocalProvider(LocalComposeWindow provides null) {
                    FlareTheme(isDarkTheme = false) {
                        Box(Modifier.width(448.dp).height(800.dp)) {
                            DesktopPluginManagementScreen(onBack = {}, onOpenPlugin = { opened = it })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Create with AI").assertIsDisplayed().performClick()
        val authoring = koinGet<PluginAuthoring>()
        compose.waitUntil(5_000) { authoring.plugins.value.size == 1 && opened != null }
        compose.runOnIdle {
            assertEquals(
                authoring.plugins.value
                    .single()
                    .conversationId,
                opened,
            )
        }
        compose.onNodeWithText("New plugin").assertIsDisplayed()
        val firstConversation = opened
        compose.onNodeWithText("Create with AI").performClick()
        compose.waitUntil(5_000) { authoring.plugins.value.size == 2 && opened != firstConversation }
        compose.runOnIdle {
            assertEquals(
                2,
                authoring.plugins.value
                    .map { it.conversationId }
                    .toSet()
                    .size,
            )
        }
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
                                        dev.dimension.flare.feature.plugin.installer.PluginSensitivePermissionV1(
                                            dev.dimension.flare.feature.plugin.installer.PluginSensitivePermissionTypeV1.MediaNetwork,
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
    fun longRepositoryUrlKeepsUninstallAndUpdateActionsVisible() {
        val repository = GitHubPluginRepositoryV1(1, "Tlaster", "flare-plugin-github-e2e-20261028")
        val plugin =
            PluginManagementItemV1(
                pluginId = "fixture.plugin",
                platformId = "Fixture",
                name = "Fixture plugin",
                nameText = UiText.Raw("Fixture plugin"),
                version = "1.0.0",
                enabled = true,
                running = true,
                pendingRestart = false,
                source = PluginInstallSourceV1.GitHub(repository, "v1.0.0", "a".repeat(40)),
                capabilities = emptySet(),
                iconPath = root.resolve("missing-icon.png").toString(),
                sourceUrl = repository.url,
                availableUpdate = GitHubPluginVersionV1("v1.1.0", "1.1.0", "b".repeat(40)),
            )
        var removed: String? = null
        var updated: String? = null
        compose.setContent {
            if (visible) {
                FlareTheme(isDarkTheme = false) {
                    Box(Modifier.width(448.dp).height(800.dp).clipToBounds()) {
                        PluginItem(
                            plugin = plugin,
                            busy = false,
                            onSetEnabled = { _, _ -> },
                            onUninstall = { removed = it },
                            onUpdate = { updated = it },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Update v1.1.0").assertIsDisplayed().performClick()
        assertEquals(plugin.pluginId, updated)
        compose.onNodeWithContentDescription("Uninstall").assertIsDisplayed().performClick()
        assertEquals(plugin.pluginId, removed)
    }

    @Test
    fun narrowWindowKeepsFileInstallOnOneLineAndGitHubInTheMenu() {
        compose.setContent {
            if (visible) {
                CompositionLocalProvider(LocalComposeWindow provides null) {
                    FlareTheme(isDarkTheme = false) {
                        // A 520 dp app window leaves 448 dp after its navigation rail.
                        Box(Modifier.width(448.dp).height(800.dp)) {
                            DesktopPluginManagementScreen(onBack = {})
                        }
                    }
                }
            }
        }
        val install = compose.onNodeWithText("Install .fpp", useUnmergedTree = true)
        install.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        install.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        compose.onNodeWithText("Install from GitHub").assertDoesNotExist()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Install from GitHub").assertIsDisplayed().performClick()
        compose.onNodeWithText("GitHub repository URL").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("GitHub repository URL").assertDoesNotExist()
    }
}
