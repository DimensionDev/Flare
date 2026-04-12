package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.InstanceResources
import dev.dimension.flare.data.network.mastodon.api.JoinMastodonResources
import dev.dimension.flare.data.network.mastodon.api.createInstanceResources
import dev.dimension.flare.data.network.mastodon.api.createJoinMastodonResources

public object JoinMastodonService {
    private val resources by lazy { ktorfit("https://api.joinmastodon.org/").createJoinMastodonResources() }

    public suspend fun servers(): List<dev.dimension.flare.data.network.mastodon.api.model.MastodonInstanceElement> = resources.servers()
}

public class MastodonInstanceService(
    public val baseUrl: String,
) {
    private val resources by lazy { ktorfit(baseUrl).createInstanceResources() }

    public suspend fun instance(): dev.dimension.flare.data.network.mastodon.api.model.InstanceData = resources.instance()

    public suspend fun instanceV1(): dev.dimension.flare.data.network.mastodon.api.model.InstanceInfoV1 = resources.instanceV1()
}
