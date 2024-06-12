package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.JoinMastodonResources
import dev.dimension.flare.data.network.mastodon.api.createJoinMastodonResources

internal object JoinMastodonService :
    JoinMastodonResources by ktorfit("https://api.joinmastodon.org/").createJoinMastodonResources()
