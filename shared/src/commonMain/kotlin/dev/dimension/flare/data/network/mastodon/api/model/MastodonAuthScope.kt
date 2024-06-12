package dev.dimension.flare.data.network.mastodon.api.model

internal enum class MastodonAuthScope(
    val value: String,
) {
    Read("read"),
    Write("write"),
    Follow("follow"),
    Push("push"),
}
