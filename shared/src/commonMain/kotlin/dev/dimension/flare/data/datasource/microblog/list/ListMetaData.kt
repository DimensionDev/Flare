package dev.dimension.flare.data.datasource.microblog.list

import dev.dimension.flare.common.FileItem

public data class ListMetaData(
    val title: String,
    val description: String? = null,
    val avatar: FileItem? = null,
)
