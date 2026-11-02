package dev.dimension.flare.data.datastore.model

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Serializable

/** Device-local account selection, excluded from exported settings. */
@Serializable
internal data class AccountSelectionPreferences(
    val selectedAccountKey: MicroBlogKey? = null,
)
