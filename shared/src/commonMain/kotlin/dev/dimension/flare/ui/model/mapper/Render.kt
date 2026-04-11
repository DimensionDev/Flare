package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.render.toUi
import kotlin.time.Instant

internal fun DbRssSources.render() =
    UiRssSource(
        url = url,
        title = title,
        lastUpdate = Instant.fromEpochMilliseconds(lastUpdate).toUi(),
        id = id,
        favIcon = icon,
        openInBrowser = openInBrowser,
        type = type,
    )
