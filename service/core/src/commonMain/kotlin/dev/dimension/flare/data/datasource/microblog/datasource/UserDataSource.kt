package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.UserHandler

public interface UserDataSource {
    public val userHandler: UserHandler
}
