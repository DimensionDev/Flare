package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.ui.model.UiProfile

internal fun UiProfile.toDbUser(host: String = this.host ?: key.host) =
    DbUser(
        userKey = key,
        name = name.raw,
        handle = handle,
        host = host,
        content = this,
    )
