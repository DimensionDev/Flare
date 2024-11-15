package dev.dimension.flare.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.window.ComposeUIViewController
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.status.TimelineComponent
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import platform.UIKit.UIViewController

@Suppress("FunctionName")
fun TimelineViewController(
    presenter: TimelinePresenter,
    accountType: AccountType,
    darkMode: Boolean,
    onOpenLink: (String) -> Unit,
): UIViewController =
    ComposeUIViewController {
        MaterialTheme(
            colorScheme = if (darkMode) darkColorScheme() else lightColorScheme(),
        ) {
            CompositionLocalProvider(
                LocalUriHandler provides
                    remember {
                        object : UriHandler {
                            override fun openUri(uri: String) {
                                onOpenLink(uri)
                            }
                        }
                    },
            ) {
                TimelineComponent(
                    presenter = presenter,
                    accountType = accountType,
                )
            }
        }
    }
