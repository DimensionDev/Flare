package dev.dimension.flare.data.network.mastodon.api.model

internal enum class SearchType(val value: String) {
    Accounts("accounts"),
    HashTags("hashtags"),
    Statuses("statuses"),
}
