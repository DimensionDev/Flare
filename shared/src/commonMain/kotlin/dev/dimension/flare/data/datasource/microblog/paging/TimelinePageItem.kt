@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.cache.model.applyTranslation
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.concurrent.atomics.AtomicReference

/** The single retained representation shared by the page cache and Paging/UI. */
internal class TimelinePageItem(
    val identity: DbTimelinePageIdentity,
    val baseItem: UiTimelineV2,
    private val translationsByStatusId: Map<String, List<DbTranslation>>,
) {
    val statusId: String
        get() = identity.statusId

    val sortId: Long
        get() = identity.sortId

    private val cachedRender = AtomicReference<CachedRender?>(null)

    fun toUi(options: TranslationDisplayOptions): UiTimelineV2 {
        cachedRender.load()?.takeIf { it.options == options }?.let { return it.item }
        val rendered =
            baseItem.applyTranslation(options) { accountType, statusKey ->
                @Suppress("UNCHECKED_CAST")
                translationsByStatusId[
                    DbStatus.createId(
                        accountType = accountType as DbAccountType,
                        statusKey = statusKey,
                    ),
                ].orEmpty()
            }
        cachedRender.store(CachedRender(options, rendered))
        return rendered
    }

    private data class CachedRender(
        val options: TranslationDisplayOptions,
        val item: UiTimelineV2,
    )
}
