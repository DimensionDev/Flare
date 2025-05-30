package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.BuildConfig
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar

// @Destination<RootGraph>(
//    wrappers = [ThemeWrapper::class],
// )
// @Composable
// internal fun AboutRoute(navigator: ProxyDestinationsNavigator) {
//    AboutScreen(
//        onBack = navigator::navigateUp,
//    )
// }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_about_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    ) {
        AboutScreenContent(
            version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            modifier =
                Modifier
                    .padding(it),
        )
    }
}
