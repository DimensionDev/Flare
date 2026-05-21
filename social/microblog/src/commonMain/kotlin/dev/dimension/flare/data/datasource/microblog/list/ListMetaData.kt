package dev.dimension.flare.data.datasource.microblog.list

import dev.dimension.flare.data.io.FileItem

public data class ListMetaData(
    val title: String,
    val description: String? = null,
    val avatar: FileItem? = null,
)
