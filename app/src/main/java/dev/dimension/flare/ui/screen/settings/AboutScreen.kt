package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Discord
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Telegram
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.Lock
import dev.dimension.flare.BuildConfig
import dev.dimension.flare.R
import dev.dimension.flare.model.ilink
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.theme.segmentedShapes2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val uriHandler = LocalUriHandler.current
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_about_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(id = R.string.app_name),
                modifier =
                    Modifier
                        .height(128.dp),
            )
            Spacer(Modifier.height(16.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_logo_text),
                contentDescription = stringResource(id = R.string.app_name),
                modifier =
                    Modifier
                        .height(32.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.settings_about_description),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SegmentedListItem(
                    content = {
                        Text(text = stringResource(id = R.string.settings_about_source_code))
                    },
                    supportingContent = {
                        Text(
                            text = "https://github.com/DimensionDev/Flare",
                        )
                    },
                    onClick = {
                        uriHandler.openUri("https://github.com/DimensionDev/Flare")
                    },
                    shapes = ListItemDefaults.segmentedShapes2(0, 5),
                    leadingContent = {
                        Icon(
                            imageVector = FontAwesomeIcons.Brands.Github,
                            contentDescription = "GitHub",
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                SegmentedListItem(
                    content = {
                        Text(text = stringResource(id = R.string.settings_about_telegram))
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.settings_about_telegram_description),
                        )
                    },
                    onClick = {
                        uriHandler.openUri(ilink)
                    },
                    shapes = ListItemDefaults.segmentedShapes2(1, 5),
                    leadingContent = {
                        Icon(
                            imageVector = FontAwesomeIcons.Brands.Telegram,
                            contentDescription = stringResource(id = R.string.settings_about_telegram),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                SegmentedListItem(
                    content = {
                        Text(text = stringResource(id = R.string.settings_about_discord))
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.settings_about_discord_description),
                        )
                    },
                    onClick = {
                        uriHandler.openUri("https://discord.gg/De9NhXBryT")
                    },
                    shapes = ListItemDefaults.segmentedShapes2(2, 5),
                    leadingContent = {
                        Icon(
                            imageVector = FontAwesomeIcons.Brands.Discord,
                            contentDescription = stringResource(id = R.string.settings_about_discord),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                SegmentedListItem(
                    content = {
                        Text(text = stringResource(id = R.string.settings_about_localization))
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.settings_about_localization_description),
                        )
                    },
                    onClick = {
                        uriHandler.openUri("https://crowdin.com/project/flareapp")
                    },
                    shapes = ListItemDefaults.segmentedShapes2(3, 5),
                    leadingContent = {
                        Icon(
                            imageVector = FontAwesomeIcons.Solid.Language,
                            contentDescription = stringResource(id = R.string.settings_about_localization),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                SegmentedListItem(
                    content = {
                        Text(text = stringResource(id = R.string.settings_privacy_policy))
                    },
                    supportingContent = {
                        Text(
                            text = "https://legal.mask.io/maskbook",
                        )
                    },
                    onClick = {
                        uriHandler.openUri("https://legal.mask.io/maskbook/")
                    },
                    shapes = ListItemDefaults.segmentedShapes2(4, 5),
                    leadingContent = {
                        Icon(
                            imageVector = FontAwesomeIcons.Solid.Lock,
                            contentDescription = stringResource(id = R.string.settings_privacy_policy),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorSpaceScreen(onBack: () -> Unit) {
    val colors =
        mapOf(
            "primary" to MaterialTheme.colorScheme.primary,
            "onPrimary" to MaterialTheme.colorScheme.onPrimary,
            "primaryContainer" to MaterialTheme.colorScheme.primaryContainer,
            "onPrimaryContainer" to MaterialTheme.colorScheme.onPrimaryContainer,
            "inversePrimary" to MaterialTheme.colorScheme.inversePrimary,
            "secondary" to MaterialTheme.colorScheme.secondary,
            "onSecondary" to MaterialTheme.colorScheme.onSecondary,
            "secondaryContainer" to MaterialTheme.colorScheme.secondaryContainer,
            "onSecondaryContainer" to MaterialTheme.colorScheme.onSecondaryContainer,
            "tertiary" to MaterialTheme.colorScheme.tertiary,
            "onTertiary" to MaterialTheme.colorScheme.onTertiary,
            "tertiaryContainer" to MaterialTheme.colorScheme.tertiaryContainer,
            "onTertiaryContainer" to MaterialTheme.colorScheme.onTertiaryContainer,
            "background" to MaterialTheme.colorScheme.background,
            "onBackground" to MaterialTheme.colorScheme.onBackground,
            "surface" to MaterialTheme.colorScheme.surface,
            "onSurface" to MaterialTheme.colorScheme.onSurface,
            "surfaceVariant" to MaterialTheme.colorScheme.surfaceVariant,
            "onSurfaceVariant" to MaterialTheme.colorScheme.onSurfaceVariant,
            "surfaceTint" to MaterialTheme.colorScheme.surfaceTint,
            "inverseSurface" to MaterialTheme.colorScheme.inverseSurface,
            "inverseOnSurface" to MaterialTheme.colorScheme.inverseOnSurface,
            "error" to MaterialTheme.colorScheme.error,
            "onError" to MaterialTheme.colorScheme.onError,
            "errorContainer" to MaterialTheme.colorScheme.errorContainer,
            "onErrorContainer" to MaterialTheme.colorScheme.onErrorContainer,
            "outline" to MaterialTheme.colorScheme.outline,
            "outlineVariant" to MaterialTheme.colorScheme.outlineVariant,
            "scrim" to MaterialTheme.colorScheme.scrim,
            "surfaceBright" to MaterialTheme.colorScheme.surfaceBright,
            "surfaceDim" to MaterialTheme.colorScheme.surfaceDim,
            "surfaceContainer" to MaterialTheme.colorScheme.surfaceContainer,
            "surfaceContainerHigh" to MaterialTheme.colorScheme.surfaceContainerHigh,
            "surfaceContainerHighest" to MaterialTheme.colorScheme.surfaceContainerHighest,
            "surfaceContainerLow" to MaterialTheme.colorScheme.surfaceContainerLow,
            "surfaceContainerLowest" to MaterialTheme.colorScheme.surfaceContainerLowest,
            "primaryFixed" to MaterialTheme.colorScheme.primaryFixed,
            "primaryFixedDim" to MaterialTheme.colorScheme.primaryFixedDim,
            "onPrimaryFixed" to MaterialTheme.colorScheme.onPrimaryFixed,
            "onPrimaryFixedVariant" to MaterialTheme.colorScheme.onPrimaryFixedVariant,
            "secondaryFixed" to MaterialTheme.colorScheme.secondaryFixed,
            "secondaryFixedDim" to MaterialTheme.colorScheme.secondaryFixedDim,
            "onSecondaryFixed" to MaterialTheme.colorScheme.onSecondaryFixed,
            "onSecondaryFixedVariant" to MaterialTheme.colorScheme.onSecondaryFixedVariant,
            "tertiaryFixed" to MaterialTheme.colorScheme.tertiaryFixed,
            "tertiaryFixedDim" to MaterialTheme.colorScheme.tertiaryFixedDim,
            "onTertiaryFixed" to MaterialTheme.colorScheme.onTertiaryFixed,
            "onTertiaryFixedVariant" to MaterialTheme.colorScheme.onTertiaryFixedVariant,
        )
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = "Color Space")
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
                    .padding(horizontal = 16.dp),
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement
                    .spacedBy(2.dp),
        ) {
            colors.onEachIndexed { index, (name, color) ->
                ListItem(
                    modifier =
                        Modifier
                            .listCard(
                                index = index,
                                totalCount = colors.size, // Adjust this count base on the number of items
                            ),
                    overlineContent = {
                        Text(
                            text = "#${color.toArgb().toHexString().uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    supportingContent = {
                        Box(
                            modifier =
                                Modifier
                                    .background(color)
                                    .fillMaxWidth()
                                    .height(36.dp),
                        )
                    },
                    elevation = ListItemDefaults.elevation(),
                    content = { Text(text = name) },
                )
            }
        }
    }
}
