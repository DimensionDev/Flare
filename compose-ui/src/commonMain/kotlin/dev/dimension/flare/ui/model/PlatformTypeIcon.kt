package dev.dimension.flare.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Bluesky
import compose.icons.fontawesomeicons.brands.Mastodon
import compose.icons.fontawesomeicons.brands.Pix
import compose.icons.fontawesomeicons.brands.Pixiv
import compose.icons.fontawesomeicons.brands.Weibo
import compose.icons.fontawesomeicons.brands.XTwitter
import compose.icons.fontawesomeicons.solid.Image
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.icons.Misskey
import dev.dimension.flare.ui.icons.Nostr

public val PlatformType.brandIcon: ImageVector
    get() =
        when (this) {
            PlatformType.Nostr -> FontAwesomeIcons.Brands.Nostr
            PlatformType.Mastodon -> FontAwesomeIcons.Brands.Mastodon
            PlatformType.Misskey -> FontAwesomeIcons.Brands.Misskey
            PlatformType.Bluesky -> FontAwesomeIcons.Brands.Bluesky
            PlatformType.Pixiv -> FontAwesomeIcons.Brands.Pixiv
            PlatformType.Fanbox -> FontAwesomeIcons.Brands.Pixiv
            PlatformType.xQt -> FontAwesomeIcons.Brands.XTwitter
            PlatformType.VVo -> FontAwesomeIcons.Brands.Weibo
        }
