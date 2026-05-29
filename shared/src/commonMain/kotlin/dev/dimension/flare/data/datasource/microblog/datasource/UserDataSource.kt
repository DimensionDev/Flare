package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface UserDataSource {
    public val userHandler: UserHandler
}
