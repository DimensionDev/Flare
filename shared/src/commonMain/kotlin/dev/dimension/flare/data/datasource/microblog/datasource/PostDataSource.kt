package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler

internal interface PostDataSource {
    val postHandler: PostHandler
    val postEventHandler: PostEventHandler
}
