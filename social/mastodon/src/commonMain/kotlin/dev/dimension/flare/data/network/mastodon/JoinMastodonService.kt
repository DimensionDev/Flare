package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.InstanceResources
import dev.dimension.flare.data.network.mastodon.api.JoinMastodonResources
import dev.dimension.flare.data.network.mastodon.api.createInstanceResources
import dev.dimension.flare.data.network.mastodon.api.createJoinMastodonResources

internal object JoinMastodonService :
    JoinMastodonResources by ktorfit("https://api.joinmastodon.org/").createJoinMastodonResources()

internal class MastodonInstanceService(
    val baseUrl: String,
) {
    private val resources: InstanceResources = ktorfit(baseUrl).createInstanceResources()

    internal suspend fun instance() = resources.instance()

    internal suspend fun instanceV1() = resources.instanceV1()
}
