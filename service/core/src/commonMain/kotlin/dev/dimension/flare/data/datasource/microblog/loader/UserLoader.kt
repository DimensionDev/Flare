package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile

public interface UserLoader {
    public suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile

    public suspend fun userById(id: String): UiProfile
}
