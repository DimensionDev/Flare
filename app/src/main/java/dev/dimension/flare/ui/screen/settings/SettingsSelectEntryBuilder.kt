package dev.dimension.flare.ui.screen.settings

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.ui.DialogSceneStrategy
import dev.dimension.flare.ui.route.Route

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderBuilder<NavKey>.settingsSelectEntryBuilder(
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
            toAppearance = {
                navigate(Route.Settings.Appearance)
            },
            toStorage = {
                navigate(Route.Settings.Storage)
            },
            toAbout = {
                navigate(Route.Settings.About)
            },
            toTabCustomization = {
                navigate(Route.Settings.TabCustomization)
            },
            toLocalFilter = {
                navigate(Route.Settings.LocalFilter)
            },
            toGuestSettings = {
                navigate(Route.Settings.GuestSetting)
            },
            toLocalHistory = {
                navigate(Route.Settings.LocalHistory)
            },
            toAiConfig = {
                navigate(Route.Settings.AiConfig)
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
            }
        )
    }

    entry<Route.Settings.Appearance>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppearanceScreen(
            onBack = onBack,
            toColorPicker = {
                navigate(Route.Settings.ColorPicker)
            }
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

    entry<Route.Settings.TabCustomization>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        TabCustomizeScreen(
            onBack = onBack,
            toAddRssSource = {
                navigate(Route.Rss.Create)
            }
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

    entry<Route.Settings.GuestSetting>(
        metadata = DialogSceneStrategy.dialog()
    ) {
        GuestSettingScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.LocalHistory>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        LocalCacheSearchScreen(
            onBack = onBack
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

    entry<Route.Settings.ColorPicker>(
        metadata = DialogSceneStrategy.dialog()
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
}
