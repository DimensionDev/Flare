package dev.dimension.flare.data.translation

import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode

public interface PreTranslationService {
    public fun enqueueStatuses(
        statuses: List<DbStatus>,
        allowLongText: Boolean = false,
    )

    public fun enqueueProfile(user: DbUser)

    public fun retryStatus(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
    )

    public fun setStatusDisplayMode(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
        mode: TranslationDisplayMode,
    )
}

public data object NoopPreTranslationService : PreTranslationService {
    public override fun enqueueStatuses(
        statuses: List<DbStatus>,
        allowLongText: Boolean,
    ): Unit = Unit

    public override fun enqueueProfile(user: DbUser): Unit = Unit

    public override fun retryStatus(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
    ): Unit = Unit

    public override fun setStatusDisplayMode(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
        mode: TranslationDisplayMode,
    ): Unit = Unit
}
