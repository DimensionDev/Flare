package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import dev.dimension.flare.ui.theme.PlatformTheme

private val GalleryTileShape = RoundedCornerShape(12.dp)
private const val GALLERY_TEXT_MAX_LINES = 5
private const val GALLERY_FEED_TITLE_MAX_LINES = 2
private const val GALLERY_FEED_DESC_MAX_LINES = 3
private val GalleryAvatarSize = 24.dp

@Composable
internal fun GalleryTimelineItem(
    item: UiTimelineV2?,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is UiTimelineV2.Post -> {
            GalleryPostTile(post = item, modifier = modifier)
        }

        is UiTimelineV2.Feed -> {
            GalleryFeedTile(feed = item, modifier = modifier)
        }

        null -> {
            GalleryPlaceholderTile(modifier = modifier)
        }

        else -> {
            // Unsupported types in gallery mode — fall back to the regular renderer inside the tile.
            Box(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .clip(GalleryTileShape)
                        .background(PlatformTheme.colorScheme.card),
            ) {
                StatusItem(item = item)
            }
        }
    }
}

@Composable
private fun GalleryPostTile(
    post: UiTimelineV2.Post,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val appearance = LocalComponentAppearance.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(GalleryTileShape)
                .background(PlatformTheme.colorScheme.card)
                .clickable {
                    post.onClicked.invoke(
                        ClickContext(launcher = { uriHandler.openUri(it) }),
                    )
                },
    ) {
        if (post.images.isNotEmpty() && appearance.showMedia) {
            val firstMedia = post.images.first()
            CompositionLocalProvider(
                LocalComponentAppearance provides
                    appearance.copy(videoAutoplay = ComponentAppearance.VideoAutoplay.NEVER),
            ) {
                MediaItem(
                    media = firstMedia,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val link =
                                    DeeplinkRoute.Media.StatusMedia(
                                        statusKey = post.statusKey,
                                        accountType = post.accountType,
                                        index = 0,
                                        preview =
                                            when (val m = firstMedia) {
                                                is UiMedia.Image -> m.previewUrl
                                                is UiMedia.Video -> m.thumbnailUrl
                                                is UiMedia.Gif -> m.previewUrl
                                                is UiMedia.Audio -> null
                                            },
                                    )
                                uriHandler.openUri(link.toUri())
                            },
                    keepAspectRatio = true,
                )
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
            ) {
                if (!post.content.isEmpty) {
                    RichText(
                        text = post.content,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = GALLERY_TEXT_MAX_LINES,
                    )
                }
            }
        }
        GalleryBottomRow(
            avatarUrl = post.user?.avatar,
            name =
                post.user
                    ?.name
                    ?.raw
                    .orEmpty(),
            isCircle = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun GalleryFeedTile(
    feed: UiTimelineV2.Feed,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val appearance = LocalComponentAppearance.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(GalleryTileShape)
                .background(PlatformTheme.colorScheme.card)
                .clickable {
                    feed.onClicked.invoke(
                        ClickContext(launcher = { uriHandler.openUri(it) }),
                    )
                },
    ) {
        val media = feed.media
        if (media != null && appearance.showMedia) {
            MediaItem(
                media = media,
                modifier = Modifier.fillMaxWidth(),
                keepAspectRatio = true,
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                feed.title?.takeIf { it.isNotBlank() }?.let {
                    PlatformText(
                        text = it,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = GALLERY_FEED_TITLE_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                feed.description?.takeIf { it.isNotBlank() }?.let {
                    PlatformText(
                        text = it,
                        style = PlatformTheme.typography.caption,
                        color = PlatformTheme.colorScheme.caption,
                        maxLines = GALLERY_FEED_DESC_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        GalleryBottomRow(
            avatarUrl = feed.source.icon,
            name = feed.source.name,
            isCircle = false,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun GalleryBottomRow(
    avatarUrl: String?,
    name: String,
    isCircle: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCircle) {
            AvatarComponent(
                data = avatarUrl,
                size = GalleryAvatarSize,
            )
        } else {
            NetworkImage(
                model = avatarUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(GalleryAvatarSize)
                        .clip(RoundedCornerShape(4.dp)),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        PlatformText(
            text = name,
            style = PlatformTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GalleryPlaceholderTile(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(GalleryTileShape)
                .background(PlatformTheme.colorScheme.card),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .placeholder(true),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(GalleryAvatarSize)
                        .clip(RoundedCornerShape(50))
                        .placeholder(true),
            )
            Spacer(modifier = Modifier.width(6.dp))
            PlatformText(
                text = "username",
                style = PlatformTheme.typography.caption,
                modifier = Modifier.placeholder(true),
            )
        }
    }
}
