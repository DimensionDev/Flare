package dev.dimension.flare.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.ComposeUIViewController
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.status.TimelineComponent
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import platform.UIKit.UIViewController

fun TimelineViewController(
    presenter: TimelinePresenter,
    accountType: AccountType,
): UIViewController = ComposeUIViewController {
    MaterialTheme {
        TimelineComponent(
            presenter = presenter,
            accountType = accountType,
        )
    }
}