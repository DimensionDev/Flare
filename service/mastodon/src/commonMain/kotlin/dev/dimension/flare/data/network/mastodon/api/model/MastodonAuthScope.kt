package dev.dimension.flare.data.network.mastodon.api.model

public enum class MastodonAuthScope(
    public val value: String,
) {
    Read("read"),
    Write("write"),
    Follow("follow"),
    Push("push"),
}
