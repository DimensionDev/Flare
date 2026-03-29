package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class PostHandler(
    val accountType: AccountType,
    val loader: PostLoader,
) : KoinComponent {
    private val database: CacheDatabase by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val appDataStore: AppDataStore by inject()
    private val preTranslationService: PreTranslationService by inject()

    private val translationDisplayFlow by lazy {
        appDataStore.appSettingsStore.data
            .map { settings ->
                TranslationDisplayOptions(
                    enabled = settings.aiConfig.translation && settings.aiConfig.preTranslation,
                    targetLanguage = settings.language.ifBlank { Locale.language },
                )
            }.distinctUntilChanged()
    }

    fun post(postKey: MicroBlogKey): Cacheable<UiTimelineV2> {
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
                    database.pagingTimelineDao().get(pagingKey, accountType = dbAccountType),
                    translationDisplayFlow,
                ) { status, paging, translationDisplayOptions ->
                    when {
                        paging != null ->
                            TimelinePagingMapper.toUi(
                                item = paging,
                                pagingKey = pagingKey,
                                useDbKeyInItemKey = false,
                                translationDisplayOptions = translationDisplayOptions,
                            )

                        status != null ->
                            TimelinePagingMapper.toUi(
                                DbPagingTimelineWithStatus(
                                    timeline =
                                        DbPagingTimeline(
                                            pagingKey = pagingKey,
                                            statusKey = postKey,
                                            sortId = 0,
                                        ),
                                    status = status,
                                ),
                                pagingKey,
                                false,
                                translationDisplayOptions = translationDisplayOptions,
                            )

                        else -> null
                    }
                }.distinctUntilChanged()
                    .mapNotNull { it }
            },
        )
    }

    fun delete(postKey: MicroBlogKey) {
        coroutineScope.launch {
            tryRun {
                loader.deleteStatus(postKey)
            }.onSuccess {
                database.connect {
                    val dbAccountType = accountType as DbAccountType
                    database.pagingTimelineDao().deleteStatus(
                        accountType = dbAccountType,
                        statusKey = postKey,
                    )
                    database.statusDao().delete(
                        statusKey = postKey,
                        accountType = dbAccountType,
                    )
                    database.statusReferenceDao().delete(postKey)
                }
            }
        }
    }
}
