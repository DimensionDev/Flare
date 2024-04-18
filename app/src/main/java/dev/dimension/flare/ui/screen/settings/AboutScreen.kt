package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github
import dev.dimension.flare.BuildConfig
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.theme.MediumAlpha

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun AboutRoute(navigator: ProxyDestinationsNavigator) {
    AboutScreen(
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_about_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .padding(it)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(id = R.string.app_name),
            )
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(id = R.string.settings_about_description),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(MediumAlpha),
            )
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(MediumAlpha),
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_about_source_code))
                },
                supportingContent = {
                    Text(
                        text = "https://github.com/DimensionDev/Flare",
                        modifier = Modifier.alpha(MediumAlpha),
                    )
                },
                modifier =
                    Modifier.clickable {
                        uriHandler.openUri("https://github.com/DimensionDev/Flare")
                    },
                leadingContent = {
                    Icon(
                        imageVector = FontAwesomeIcons.Brands.Github,
                        contentDescription = "GitHub",
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
        }
    }
}
