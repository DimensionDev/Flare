package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler

public interface PostDataSource {
    public val postHandler: PostHandler
    public val postEventHandler: PostEventHandler
}
