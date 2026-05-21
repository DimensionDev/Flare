package dev.dimension.flare.data.account

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount

public interface AccountLookup {
    public suspend fun find(accountKey: MicroBlogKey): UiAccount?
}
