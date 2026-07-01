package dev.dimension.flare.ui.screen.settings

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.component.DialogSceneStrategy2
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.agent.AgentChatHistoryScreen
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.settingsSelectEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Settings.Main>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "Settings",
            detailPlaceholder = {
                SettingsDetailPlaceholder()
            }
        )
    ) {
        SettingsScreen(
            toAccounts = {
                navigate(Route.Settings.Accounts)
            },
            toAppearanceTheme = {
                navigate(Route.Settings.AppearanceTheme)
            },
            toAppearanceLayout = {
                navigate(Route.Settings.AppearanceLayout)
            },
            toAppearanceDisplay = {
                navigate(Route.Settings.AppearanceDisplay)
            },
            toAppearanceMedia = {
                navigate(Route.Settings.AppearanceMedia)
            },
            toBehavior = {
                navigate(Route.Settings.Behavior)
            },
            toStorage = {
                navigate(Route.Settings.Storage)
            },
            toAbout = {
                navigate(Route.Settings.About)
            },
            toLocalFilter = {
                navigate(Route.Settings.LocalFilter)
            },
            toAiConfig = {
                navigate(Route.Settings.AiConfig)
            },
            toColorSpace = {
                navigate(Route.Settings.ColorSpace)
            },
            toTranslationConfig = {
                navigate(Route.Settings.TranslationConfig)
            },
            onBack = onBack,
        )
    }

    entry<Route.Settings.Accounts>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AccountsScreen(
            onBack = onBack,
            toLogin = {
                navigate(Route.ServiceSelect.Selection)
            },
            toRelogin = {
                navigate(Route.ServiceSelect.Relogin(it))
            },
            toNostrRelays = {
                navigate(Route.Settings.NostrRelays(it))
            }
        )
    }

    entry<Route.Settings.AppearanceTheme>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppearanceThemeScreen(
            onBack = onBack,
            toColorPicker = {
                navigate(Route.Settings.ColorPicker)
            }
        )
    }

    entry<Route.Settings.AppearanceLayout>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppearanceLayoutScreen(
            onBack = onBack,
            toPostActionLayout = {
                navigate(Route.Settings.PostActionLayout)
            }
        )
    }

    entry<Route.Settings.PostActionLayout>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        PostActionLayoutScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.AppearanceDisplay>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppearanceDisplayScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.AppearanceMedia>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppearanceMediaScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.Behavior>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        BehaviorScreen(
            toLinkOpenDefaults = {
                navigate(Route.Settings.LinkOpenDefaults)
            },
            onBack = onBack,
        )
    }

    entry<Route.Settings.LinkOpenDefaults>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        LinkOpenDefaultsScreen(
            onBack = onBack,
        )
    }

    entry<Route.Settings.Storage>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        StorageScreen(
            onBack = onBack,
            toAppLog = {
                navigate(Route.Settings.AppLogging)
            }
        )
    }

    entry<Route.Settings.About>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AboutScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.LocalFilter>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        LocalFilterScreen(
            onBack = onBack,
            edit = { keyword ->
                navigate(Route.Settings.LocalFilterEdit(keyword))
            },
            add = {
                navigate(Route.Settings.LocalFilterEdit(null))
            }
        )
    }

    entry<Route.Settings.LocalHistory>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        LocalCacheSearchScreen(
            onBack = onBack,
            onAskAiClick = { query, target ->
                navigate(
                    Route.LocalHistoryAgent(
                        conversationId = "local-history:${Clock.System.now().toEpochMilliseconds()}",
                        query = query,
                        target = target,
                    ),
                )
            },
        )
    }

    entry<Route.Settings.AgentHistory>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AgentChatHistoryScreen(
            onBack = onBack,
            onConversationClick = { conversationId ->
                navigate(Route.AgentChat(conversationId = conversationId))
            },
            onNewConversationClick = {
                navigate(
                    Route.AgentChat(
                        conversationId = "generic-chat:${Clock.System.now().toEpochMilliseconds()}",
                    ),
                )
            },
        )
    }

    entry<Route.Settings.AiConfig>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AiConfigScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.TranslationConfig>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        TranslationConfigScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.ColorPicker>(
        metadata = DialogSceneStrategy2.dialog()
    ) {
        ColorPickerDialog(
            onBack = onBack
        )
    }

    entry<Route.Settings.AppLogging>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppLoggingScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.NostrRelays>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) { args ->
        NostrRelaysScreen(
            accountKey = args.accountKey,
            onBack = onBack
        )
    }

    entry<Route.Settings.LocalFilterEdit>(
        metadata = ListDetailSceneStrategy.extraPane(
            sceneKey = "Settings"
        )
    ) { args ->
        LocalFilterEditDialog(
            keyword = args.keyword,
            onBack = onBack
        )
    }

    entry<Route.Settings.ColorSpace>(
        metadata = ListDetailSceneStrategy.extraPane(
            sceneKey = "Settings"
        )
    ) { args ->
        ColorSpaceScreen(
            onBack = onBack
        )
    }
}
