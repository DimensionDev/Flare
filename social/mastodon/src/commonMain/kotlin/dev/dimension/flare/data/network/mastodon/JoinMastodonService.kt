package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.InstanceResources
import dev.dimension.flare.data.network.mastodon.api.JoinMastodonResources
import dev.dimension.flare.data.network.mastodon.api.createInstanceResources
import dev.dimension.flare.data.network.mastodon.api.createJoinMastodonResources

public object JoinMastodonService :
    JoinMastodonResources by ktorfit("https://api.joinmastodon.org/").createJoinMastodonResources()

public class MastodonInstanceService(
    public val baseUrl: String,
) : InstanceResources by ktorfit(baseUrl).createInstanceResources()
