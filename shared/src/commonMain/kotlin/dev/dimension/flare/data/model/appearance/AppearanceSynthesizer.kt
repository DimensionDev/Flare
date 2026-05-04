package dev.dimension.flare.data.model.appearance

import dev.dimension.flare.data.model.AppearanceSettings
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

internal fun AppearancePatch.toAppearanceSettings(): AppearanceSettings =
    AppearanceSettings(
        theme = get(AppearanceKeys.Theme),
        dynamicTheme = get(AppearanceKeys.DynamicTheme),
        colorSeed = get(AppearanceKeys.ColorSeed),
        avatarShape = get(AppearanceKeys.AvatarShape),
        pureColorMode = get(AppearanceKeys.PureColorMode),
        showNumbers = get(AppearanceKeys.ShowNumbers),
        showLinkPreview = get(AppearanceKeys.ShowLinkPreview),
        showMedia = get(AppearanceKeys.ShowMedia),
        showSensitiveContent = get(AppearanceKeys.ShowSensitiveContent),
        videoAutoplay = get(AppearanceKeys.VideoAutoplay),
        expandMediaSize = get(AppearanceKeys.ExpandMediaSize),
        compatLinkPreview = get(AppearanceKeys.CompatLinkPreview),
        fontSizeDiff = get(AppearanceKeys.FontSizeDiff),
        lineHeightDiff = get(AppearanceKeys.LineHeightDiff),
        showComposeInHomeTimeline = get(AppearanceKeys.ShowComposeInHomeTimeline),
        bottomBarStyle = get(AppearanceKeys.BottomBarStyle),
        bottomBarBehavior = get(AppearanceKeys.BottomBarBehavior),
        inAppBrowser = get(AppearanceKeys.InAppBrowser),
        fullWidthPost = get(AppearanceKeys.FullWidthPost),
        postActionStyle = get(AppearanceKeys.PostActionStyle),
        absoluteTimestamp = get(AppearanceKeys.AbsoluteTimestamp),
        showPlatformLogo = get(AppearanceKeys.ShowPlatformLogo),
        timelineDisplayMode = get(AppearanceKeys.TimelineDisplayMode),
    )

internal fun AppearanceSettings.toPatch(): AppearancePatch =
    AppearancePatch.EMPTY
        .set(AppearanceKeys.Theme, theme)
        .set(AppearanceKeys.DynamicTheme, dynamicTheme)
        .set(AppearanceKeys.ColorSeed, colorSeed)
        .set(AppearanceKeys.AvatarShape, avatarShape)
        .set(AppearanceKeys.PureColorMode, pureColorMode)
        .set(AppearanceKeys.ShowNumbers, showNumbers)
        .set(AppearanceKeys.ShowLinkPreview, showLinkPreview)
        .set(AppearanceKeys.ShowMedia, showMedia)
        .set(AppearanceKeys.ShowSensitiveContent, showSensitiveContent)
        .set(AppearanceKeys.VideoAutoplay, videoAutoplay)
        .set(AppearanceKeys.ExpandMediaSize, expandMediaSize)
        .set(AppearanceKeys.CompatLinkPreview, compatLinkPreview)
        .set(AppearanceKeys.FontSizeDiff, fontSizeDiff)
        .set(AppearanceKeys.LineHeightDiff, lineHeightDiff)
        .set(AppearanceKeys.ShowComposeInHomeTimeline, showComposeInHomeTimeline)
        .set(AppearanceKeys.BottomBarStyle, bottomBarStyle)
        .set(AppearanceKeys.BottomBarBehavior, bottomBarBehavior)
        .set(AppearanceKeys.InAppBrowser, inAppBrowser)
        .set(AppearanceKeys.FullWidthPost, fullWidthPost)
        .set(AppearanceKeys.PostActionStyle, postActionStyle)
        .set(AppearanceKeys.AbsoluteTimestamp, absoluteTimestamp)
        .set(AppearanceKeys.ShowPlatformLogo, showPlatformLogo)
        .set(AppearanceKeys.TimelineDisplayMode, timelineDisplayMode)

@OptIn(ExperimentalSerializationApi::class)
internal fun AppearanceBag.toPatch(): AppearancePatch {
    var patch = AppearancePatch.EMPTY
    for ((id, bytes) in entries) {
        val key = AppearanceKeys.byId(id) ?: continue
        val value =
            runCatching {
                @Suppress("UNCHECKED_CAST")
                ProtoBuf.decodeFromByteArray(key.serializer as KSerializer<Any>, bytes)
            }.getOrNull() ?: continue
        @Suppress("UNCHECKED_CAST")
        patch = patch.set(key as AppearanceKey<Any>, value)
    }
    return patch
}

@OptIn(ExperimentalSerializationApi::class)
internal fun AppearancePatch.toBag(): AppearanceBag =
    AppearanceBag(
        entries =
            explicitEntries
                .mapNotNull { (id, value) ->
                    val key = AppearanceKeys.byId(id) ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    id to ProtoBuf.encodeToByteArray(key.serializer as KSerializer<Any>, value)
                }.toMap(),
    )
