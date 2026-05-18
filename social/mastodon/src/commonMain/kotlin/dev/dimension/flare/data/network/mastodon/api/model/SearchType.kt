package dev.dimension.flare.data.network.mastodon.api.model

public enum class SearchType(
    public val value: String,
) {
    Accounts("accounts"),
    HashTags("hashtags"),
    Statuses("statuses"),
}
