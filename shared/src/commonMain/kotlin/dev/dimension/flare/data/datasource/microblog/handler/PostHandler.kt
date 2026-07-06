package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.data.translation.TranslationSettingsSupport
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class PostHandler(
    public val accountType: AccountType,
    public val loader: PostLoader,
) {
    private val database: CacheDatabase by koinInject()
    private val coroutineScope: CoroutineScope by koinInject()
    private val appDataStore: AppDataStore by koinInject()
    private val preTranslationService: PreTranslationService by koinInject()

    private val translationDisplayFlow by lazy {
        TranslationSettingsSupport.displayOptionsFlow(appDataStore)
    }

    public fun post(
        postKey: MicroBlogKey,
        translationDisplay: PostTranslationDisplay = PostTranslationDisplay.UserSettings,
    ): Cacheable<UiTimelineV2> {
        val pagingKey = "post_only_$postKey"
        return Cacheable(
            fetchSource = {
                val result = loader.status(postKey)
                database.connect {
                    val item =
                        TimelinePagingMapper.toDb(
                            result,
                            pagingKey,
                        )
                    saveToDatabase(database, listOf(item))
                    preTranslationService.enqueueStatuses(
                        listOfNotNull(item.status.status.data) + item.status.references.mapNotNull { it.status?.data },
                        allowLongText = true,
                    )
                }
            },
            cacheSource = {
                val dbAccountType = accountType as DbAccountType
                combine(
                    database
                        .statusDao()
                        .getWithReferences(postKey, dbAccountType),
                    database
                        .pagingTimelineDao()
                        .get(pagingKey, accountType = dbAccountType),
                    translationDisplay.optionsFlow(translationDisplayFlow),
                ) { status, timelineStatus, translationDisplayOptions ->
                    timelineStatus?.let {
                        TimelinePagingMapper.toUi(
                            item = it,
                            pagingKey = pagingKey,
                            translationDisplayOptions = translationDisplayOptions,
                        )
                    } ?: status?.let {
                        TimelinePagingMapper.toUi(
                            item = it,
                            pagingKey = pagingKey,
                            translationDisplayOptions = translationDisplayOptions,
                        )
                    }
                }.filterNotNull()
                    .distinctUntilChanged()
            },
        )
    }

    public fun delete(postKey: MicroBlogKey): Job =
        coroutineScope.launch {
            tryRun {
                loader.deleteStatus(postKey)
            }.onSuccess {
                database.connect {
                    val dbAccountType = accountType as DbAccountType
                    database.pagingTimelineDao().deleteStatus(
                        accountType = dbAccountType,
                        statusId = DbStatus.createId(dbAccountType, postKey),
                    )
                    database.statusDao().delete(
                        statusKey = postKey,
                        accountType = dbAccountType,
                    )
                    database.statusReferenceDao().delete(DbStatus.createId(dbAccountType, postKey))
                }
            }
        }
}

@HiddenFromObjC
public enum class PostTranslationDisplay {
    UserSettings,
    Original,
}

private fun PostTranslationDisplay.optionsFlow(userSettingsFlow: Flow<TranslationDisplayOptions>): Flow<TranslationDisplayOptions> =
    when (this) {
        PostTranslationDisplay.UserSettings -> {
            userSettingsFlow
        }

        PostTranslationDisplay.Original -> {
            flowOf(
                TranslationDisplayOptions(
                    translationEnabled = false,
                    autoDisplayEnabled = false,
                    providerCacheKey = "",
                ),
            )
        }
    }
