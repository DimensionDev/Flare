package dev.dimension.flare.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import dev.dimension.flare.data.model.AppSettings
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.screen.home.HomeScreen
import dev.dimension.flare.ui.theme.FlareTheme
import org.koin.compose.koinInject

@Composable
fun AppContainer(afterInit: () -> Unit) {
    FlareApp {
        FlareTheme {
            HomeScreen(
                afterInit = afterInit,
            )
        }
    }
}

@Composable
fun FlareApp(content: @Composable () -> Unit) {
    val settingsRepository = koinInject<SettingsRepository>()
    val appearanceSettings by settingsRepository.appearanceSettings.collectAsState(
        AppearanceSettings(),
    )
    val originalUriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val uriHandler =
        remember(appearanceSettings.inAppBrowser) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    if (uri.startsWith("http://") || uri.startsWith("https://")) {
                        openInBrowser(context, uri, appearanceSettings.inAppBrowser)
                    } else {
                        originalUriHandler.openUri(uri)
                    }
                }
            }
        }

    val appSettings by settingsRepository.appSettings.collectAsState(AppSettings(""))
    CompositionLocalProvider(
        LocalUriHandler provides uriHandler,
        LocalAppearanceSettings provides appearanceSettings,
        LocalComponentAppearance provides
            remember(appearanceSettings, appSettings.aiConfig) {
                ComponentAppearance(
                    dynamicTheme = appearanceSettings.dynamicTheme,
                    avatarShape =
                        when (appearanceSettings.avatarShape) {
                            AvatarShape.CIRCLE -> ComponentAppearance.AvatarShape.CIRCLE
                            AvatarShape.SQUARE -> ComponentAppearance.AvatarShape.SQUARE
                        },
                    showActions = appearanceSettings.showActions,
                    showNumbers = appearanceSettings.showNumbers,
                    showLinkPreview = appearanceSettings.showLinkPreview,
                    showMedia = appearanceSettings.showMedia,
                    showSensitiveContent = appearanceSettings.showSensitiveContent,
                    videoAutoplay =
                        when (appearanceSettings.videoAutoplay) {
                            VideoAutoplay.ALWAYS -> ComponentAppearance.VideoAutoplay.ALWAYS
                            VideoAutoplay.WIFI -> ComponentAppearance.VideoAutoplay.WIFI
                            VideoAutoplay.NEVER -> ComponentAppearance.VideoAutoplay.NEVER
                        },
                    expandMediaSize = appearanceSettings.expandMediaSize,
                    compatLinkPreview = appearanceSettings.compatLinkPreview,
                    aiConfig =
                        ComponentAppearance.AiConfig(
                            translation = appSettings.aiConfig.translation,
                            tldr = appSettings.aiConfig.tldr,
                        ),
                    fullWidthPost = appearanceSettings.fullWidthPost,
                )
            },
        content = content,
    )
}

private fun getNonSelfBrowserPackageName(
    context: Context,
    url: String,
): String? {
    val packageName = context.packageName
    val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
    val packageManager = context.packageManager
    val list = packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
    val defaultResolveInfo = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
    if (defaultResolveInfo != null && defaultResolveInfo.activityInfo.packageName != packageName) {
        return defaultResolveInfo.activityInfo.packageName
    }
    for (info in list) {
        if (info.activityInfo.packageName != packageName) {
            return info.activityInfo.packageName
        }
    }

    return null
}

private fun openInBrowser(
    context: Context,
    url: String,
    inAppBrowser: Boolean,
) {
    val targetPackage = getNonSelfBrowserPackageName(context, url)
    if (targetPackage != null) {
        if (inAppBrowser) {
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.intent.setPackage(targetPackage)
            customTabsIntent.launchUrl(context, url.toUri())
        } else {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.setPackage(targetPackage)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
