package dev.dimension.flare.data.datastore.model

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Serializable

@Serializable
public data class ComposeConfigData(
    val visibility: ComposeVisibility = ComposeVisibility.Public,
    val lastAccounts: List<MicroBlogKey> = emptyList(),
)

@Serializable
public enum class ComposeVisibility {
    Public,
    Home,
    Followers,
    Specified,
    Channel,
}
