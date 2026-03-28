package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.mapper.upsertUser
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.applyTranslation
import dev.dimension.flare.data.database.cache.model.translationEntityKey
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class UserHandler(
    private val host: String,
    private val loader: UserLoader,
) : KoinComponent {
    private val database: CacheDatabase by inject()
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

    fun userByHandleAndHost(uiHandle: UiHandle) =
        Cacheable(
            fetchSource = {
                val user = loader.userByHandleAndHost(uiHandle)
                val dbUser = user.toDbUser()
                database.upsertUser(
                    dbUser,
                )
                preTranslationService.enqueueProfile(dbUser)
            },
            cacheSource = {
                translatedUserFlow(
                    database
                        .userDao()
                        .findByCanonicalHandleAndHost(
                            canonicalHandle = uiHandle.canonical,
                            host = uiHandle.normalizedHost,
                        ).distinctUntilChanged(),
                )
            },
        )

    fun userById(id: String) =
        Cacheable(
            fetchSource = {
                val user = loader.userById(id)
                val dbUser = user.toDbUser()
                database.upsertUser(
                    dbUser,
                )
                preTranslationService.enqueueProfile(dbUser)
            },
            cacheSource = {
                translatedUserFlow(
                    database
                        .userDao()
                        .findByKey(
                            MicroBlogKey(
                                id = id,
                                host = host,
                            ),
                        ).distinctUntilChanged(),
                )
            },
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun translatedUserFlow(userFlow: kotlinx.coroutines.flow.Flow<dev.dimension.flare.data.database.cache.model.DbUser?>) =
        combine(userFlow, translationDisplayFlow) { user, translationDisplayOptions ->
            user to translationDisplayOptions
        }.flatMapLatest { (user, translationDisplayOptions) ->
            if (user == null || !translationDisplayOptions.enabled) {
                flowOf(user?.content)
            } else {
                combine(
                    flowOf(user),
                    database
                        .translationDao()
                        .find(
                            entityType = TranslationEntityType.Profile,
                            entityKey = user.translationEntityKey(),
                            targetLanguage = translationDisplayOptions.targetLanguage,
                        ),
                ) { dbUser, translation ->
                    dbUser.content.applyTranslation(
                        options = translationDisplayOptions,
                        translation = translation,
                    )
                }
            }
        }.mapNotNull { it }
}
