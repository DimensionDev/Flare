package dev.dimension.flare.data.datasource.bluesky

import dev.dimension.flare.ui.model.mapper.bskyJson
import sh.christian.ozone.api.model.JsonContent
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent

public inline fun <reified T : Any> T.bskyJson(): JsonContent = bskyJson.encodeAsJsonContent(this)
