package dev.dimension.flare.ui.screen.settings

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.ui.DialogSceneStrategy
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderBuilder<NavKey>.settingsSelectEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Settings.Main> {
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
        )
    }

    entry<Route.Settings.Accounts> {
        AccountsScreen(
            onBack = onBack,
            toLogin = {
                navigate(Route.ServiceSelect.Selection)
            }
        )
    }

    entry<Route.Settings.Appearance> {
        AppearanceScreen(
            onBack = onBack,
            toColorPicker = {
                navigate(Route.Settings.ColorPicker)
            }
        )
    }

    entry<Route.Settings.Storage> {
        StorageScreen(
            onBack = onBack,
            toAppLog = {
                navigate(Route.Settings.AppLogging)
            }
        )
    }

    entry<Route.Settings.About> {
        AboutScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.TabCustomization> {
        TabCustomizeScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.LocalFilter> {
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

    entry<Route.Settings.LocalHistory> {
        LocalCacheSearchScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.AiConfig> {
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

    entry<Route.Settings.AppLogging> {
        AppLoggingScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.LocalFilterEdit> { args ->
        LocalFilterEditDialog(
            keyword = args.keyword,
            onBack = onBack
        )
    }
}
