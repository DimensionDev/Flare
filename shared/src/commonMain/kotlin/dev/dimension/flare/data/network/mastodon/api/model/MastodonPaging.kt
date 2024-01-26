package dev.dimension.flare.data.network.mastodon.api.model

import de.jensklingenberg.ktorfit.Response

internal class MastodonPaging<T>(
    data: List<T>,
    val next: String? = null,
    val prev: String? = null,
) : List<T> by data {
    companion object {
        fun <T> from(response: Response<List<T>>): MastodonPaging<T> {
            val link = response.headers["link"]
            val next = link?.let { "max_id=(\\d+)".toRegex().find(it) }?.groupValues?.getOrNull(1)
            val prev = link?.let { "min_id=(\\d+)".toRegex().find(it) }?.groupValues?.getOrNull(1)
            return MastodonPaging(
                data = response.body() ?: emptyList(),
                next = next,
                prev = prev,
            )
        }
    }
}
