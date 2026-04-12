package dev.dimension.flare.data.translation

import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

public interface PreTranslationService {
    public fun enqueueStatuses(
        statuses: List<DbStatus>,
        allowLongText: Boolean = false,
    )

    public fun enqueueProfile(user: DbUser)

    public fun retryStatus(
        accountType: AccountType,
        statusKey: MicroBlogKey,
    )

    public fun setStatusDisplayMode(
        accountType: AccountType,
        statusKey: MicroBlogKey,
        mode: TranslationDisplayMode,
    )
}

public data object NoopPreTranslationService : PreTranslationService {
    override fun enqueueStatuses(
        statuses: List<DbStatus>,
        allowLongText: Boolean,
    ): Unit = Unit

    override fun enqueueProfile(user: DbUser): Unit = Unit

    override fun retryStatus(
        accountType: AccountType,
        statusKey: MicroBlogKey,
    ): Unit = Unit

    override fun setStatusDisplayMode(
        accountType: AccountType,
        statusKey: MicroBlogKey,
        mode: TranslationDisplayMode,
    ): Unit = Unit
}
