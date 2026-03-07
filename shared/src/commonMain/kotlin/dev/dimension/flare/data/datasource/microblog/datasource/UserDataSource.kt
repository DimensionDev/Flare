package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.UserHandler

internal interface UserDataSource {
    val userHandler: UserHandler
}
