package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Discord
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Line
import compose.icons.fontawesomeicons.brands.Telegram
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.Lock
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.app_name
import dev.dimension.flare.compose.ui.ic_launcher_foreground
import dev.dimension.flare.compose.ui.ic_logo_text
import dev.dimension.flare.compose.ui.settings_about_description
import dev.dimension.flare.compose.ui.settings_about_discord
import dev.dimension.flare.compose.ui.settings_about_discord_description
import dev.dimension.flare.compose.ui.settings_about_line
import dev.dimension.flare.compose.ui.settings_about_line_description
import dev.dimension.flare.compose.ui.settings_about_localization
import dev.dimension.flare.compose.ui.settings_about_localization_description
import dev.dimension.flare.compose.ui.settings_about_source_code
import dev.dimension.flare.compose.ui.settings_about_telegram
import dev.dimension.flare.compose.ui.settings_about_telegram_description
import dev.dimension.flare.compose.ui.settings_privacy_policy
import dev.dimension.flare.ui.component.platform.PlatformIcon
import dev.dimension.flare.ui.component.platform.PlatformSegmentedListItem
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
public fun AboutScreenContent(
    version: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .then(modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_launcher_foreground),
            contentDescription = stringResource(resource = Res.string.app_name),
            modifier =
                Modifier
                    .height(128.dp),
        )
        Spacer(Modifier.height(16.dp))
        PlatformIcon(
            painterResource(Res.drawable.ic_logo_text),
            contentDescription = stringResource(resource = Res.string.app_name),
            modifier =
                Modifier
                    .height(32.dp),
        )
        Spacer(Modifier.height(16.dp))
        PlatformText(
            text = stringResource(resource = Res.string.settings_about_description),
            textAlign = TextAlign.Center,
            style = PlatformTheme.typography.caption,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp),
            color = PlatformTheme.colorScheme.caption,
        )
        PlatformText(
            text = version,
            style = PlatformTheme.typography.caption,
            color = PlatformTheme.colorScheme.caption,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PlatformSegmentedListItem(
                headlineContent = {
                    PlatformText(text = stringResource(resource = Res.string.settings_about_source_code))
                },
                supportingContent = {
                    PlatformText(
                        text = "https://github.com/DimensionDev/Flare",
                    )
                },
                onClick = {
                    uriHandler.openUri("https://github.com/DimensionDev/Flare")
                },
                index = 0,
                totalCount = 6,
                leadingContent = {
                    PlatformIcon(
                        imageVector = FontAwesomeIcons.Brands.Github,
                        contentDescription = "GitHub",
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            PlatformSegmentedListItem(
                headlineContent = {
                    PlatformText(text = stringResource(resource = Res.string.settings_about_telegram))
                },
                supportingContent = {
                    PlatformText(
                        text = stringResource(resource = Res.string.settings_about_telegram_description),
                    )
                },
                onClick = {
                    uriHandler.openUri("https://t.me/+0UtcP6_qcDoyOWE1")
                },
                index = 1,
                totalCount = 6,
                leadingContent = {
                    PlatformIcon(
                        imageVector = FontAwesomeIcons.Brands.Telegram,
                        contentDescription = stringResource(resource = Res.string.settings_about_telegram),
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            PlatformSegmentedListItem(
                headlineContent = {
                    PlatformText(text = stringResource(resource = Res.string.settings_about_discord))
                },
                supportingContent = {
                    PlatformText(
                        text = stringResource(resource = Res.string.settings_about_discord_description),
                    )
                },
                onClick = {
                    uriHandler.openUri("https://discord.gg/De9NhXBryT")
                },
                index = 2,
                totalCount = 6,
                leadingContent = {
                    PlatformIcon(
                        imageVector = FontAwesomeIcons.Brands.Discord,
                        contentDescription = stringResource(resource = Res.string.settings_about_discord),
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            PlatformSegmentedListItem(
                headlineContent = {
                    PlatformText(text = stringResource(resource = Res.string.settings_about_line))
                },
                supportingContent = {
                    PlatformText(
                        text = stringResource(resource = Res.string.settings_about_line_description),
                    )
                },
                onClick = {
                    uriHandler.openUri("https://line.me/ti/g/hf95HyGJ9k")
                },
                index = 3,
                totalCount = 6,
                leadingContent = {
                    PlatformIcon(
                        imageVector = FontAwesomeIcons.Brands.Line,
                        contentDescription = stringResource(resource = Res.string.settings_about_telegram),
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            PlatformSegmentedListItem(
                headlineContent = {
                    PlatformText(text = stringResource(resource = Res.string.settings_about_localization))
                },
                supportingContent = {
                    PlatformText(
                        text = stringResource(resource = Res.string.settings_about_localization_description),
                    )
                },
                onClick = {
                    uriHandler.openUri("https://crowdin.com/project/flareapp")
                },
                index = 4,
                totalCount = 6,
                leadingContent = {
                    PlatformIcon(
                        imageVector = FontAwesomeIcons.Solid.Language,
                        contentDescription = stringResource(resource = Res.string.settings_about_localization),
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            PlatformSegmentedListItem(
                headlineContent = {
                    PlatformText(text = stringResource(resource = Res.string.settings_privacy_policy))
                },
                supportingContent = {
                    PlatformText(
                        text = "https://legal.mask.io/maskbook",
                    )
                },
                onClick = {
                    uriHandler.openUri("https://legal.mask.io/maskbook/")
                },
                index = 5,
                totalCount = 6,
                leadingContent = {
                    PlatformIcon(
                        imageVector = FontAwesomeIcons.Solid.Lock,
                        contentDescription = stringResource(resource = Res.string.settings_privacy_policy),
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
        }
    }
}
