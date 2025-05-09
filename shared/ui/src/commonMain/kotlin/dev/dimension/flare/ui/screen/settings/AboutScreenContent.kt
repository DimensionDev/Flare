package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Line
import compose.icons.fontawesomeicons.brands.Telegram
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.Lock
import dev.dimension.flare.Res
import dev.dimension.flare.app_name
import dev.dimension.flare.ic_launcher_foreground
import dev.dimension.flare.settings_about_description
import dev.dimension.flare.settings_about_line
import dev.dimension.flare.settings_about_line_description
import dev.dimension.flare.settings_about_localization
import dev.dimension.flare.settings_about_localization_description
import dev.dimension.flare.settings_about_source_code
import dev.dimension.flare.settings_about_telegram
import dev.dimension.flare.settings_about_telegram_description
import dev.dimension.flare.settings_privacy_policy
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
public fun AboutScreenContent(
    version: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_launcher_foreground),
            contentDescription = stringResource(resource = Res.string.app_name),
        )
        Text(
            text = stringResource(resource = Res.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(resource = Res.string.settings_about_description),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = version,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ListItem(
            headlineContent = {
                Text(text = stringResource(resource = Res.string.settings_about_source_code))
            },
            supportingContent = {
                Text(
                    text = "https://github.com/DimensionDev/Flare",
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
        ListItem(
            headlineContent = {
                Text(text = stringResource(resource = Res.string.settings_about_telegram))
            },
            supportingContent = {
                Text(
                    text = stringResource(resource = Res.string.settings_about_telegram_description),
                )
            },
            modifier =
                Modifier.clickable {
                    uriHandler.openUri("https://t.me/+0UtcP6_qcDoyOWE1")
                },
            leadingContent = {
                Icon(
                    imageVector = FontAwesomeIcons.Brands.Telegram,
                    contentDescription = stringResource(resource = Res.string.settings_about_telegram),
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        ListItem(
            headlineContent = {
                Text(text = stringResource(resource = Res.string.settings_about_line))
            },
            supportingContent = {
                Text(
                    text = stringResource(resource = Res.string.settings_about_line_description),
                )
            },
            modifier =
                Modifier.clickable {
                    uriHandler.openUri("https://line.me/ti/g/hf95HyGJ9k")
                },
            leadingContent = {
                Icon(
                    imageVector = FontAwesomeIcons.Brands.Line,
                    contentDescription = stringResource(resource = Res.string.settings_about_telegram),
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        ListItem(
            headlineContent = {
                Text(text = stringResource(resource = Res.string.settings_about_localization))
            },
            supportingContent = {
                Text(
                    text = stringResource(resource = Res.string.settings_about_localization_description),
                )
            },
            modifier =
                Modifier.clickable {
                    uriHandler.openUri("https://crowdin.com/project/flareapp")
                },
            leadingContent = {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Language,
                    contentDescription = stringResource(resource = Res.string.settings_about_localization),
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        ListItem(
            headlineContent = {
                Text(text = stringResource(resource = Res.string.settings_privacy_policy))
            },
            supportingContent = {
                Text(
                    text = "https://legal.mask.io/maskbook",
                )
            },
            modifier =
                Modifier.clickable {
                    uriHandler.openUri("https://legal.mask.io/maskbook/")
                },
            leadingContent = {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Lock,
                    contentDescription = stringResource(resource = Res.string.settings_privacy_policy),
                    modifier = Modifier.size(24.dp),
                )
            },
        )
    }
}
