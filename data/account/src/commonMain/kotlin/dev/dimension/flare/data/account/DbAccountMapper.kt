package dev.dimension.flare.data.account

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount

internal fun DbAccount.toUi(): UiAccount =
    when (platform_type) {
        PlatformType.Nostr -> {
            UiAccount.Nostr(
                accountKey = account_key,
            )
        }

        PlatformType.Mastodon -> {
            val credential = credential_json.decodeJson<UiAccount.Mastodon.Credential>()
            UiAccount.Mastodon(
                accountKey = account_key,
                forkType = credential.forkType,
                instance = credential.instance,
                nodeType = credential.nodeType,
            )
        }

        PlatformType.Misskey -> {
            val credential = credential_json.decodeJson<UiAccount.Misskey.Credential>()
            UiAccount.Misskey(
                accountKey = account_key,
                host = credential.host,
                nodeType = credential.nodeType,
            )
        }

        PlatformType.Bluesky -> {
            UiAccount.Bluesky(
                accountKey = account_key,
            )
        }

        PlatformType.xQt -> {
            UiAccount.XQT(
                accountKey = account_key,
            )
        }

        PlatformType.VVo -> {
            UiAccount.VVo(
                accountKey = account_key,
            )
        }
    }
