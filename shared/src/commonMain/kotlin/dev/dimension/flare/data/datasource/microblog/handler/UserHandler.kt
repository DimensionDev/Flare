package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class UserHandler(
    val accountKey: MicroBlogKey,
    val loader: UserLoader,
) : KoinComponent {
    private val database: CacheDatabase by inject()

    fun userByHandleAndHost(
        handle: String,
        host: String,
    ) = Cacheable(
        fetchSource = {
            val user = loader.userByHandleAndHost(handle, host)
            database.userDao().insert(
                DbUser(
                    userKey = user.key,
                    name = user.name.raw,
                    handle = user.handle,
                    host = user.key.host,
                    content = user,
                ),
            )
        },
        cacheSource = {
            database
                .userDao()
                .findByHandleAndHost(handle, host)
                .distinctUntilChanged()
                .mapNotNull { it?.content }
        },
    )

    fun userById(id: String) =
        Cacheable(
            fetchSource = {
                val user = loader.userById(id)
                database.userDao().insert(
                    DbUser(
                        userKey = user.key,
                        name = user.name.raw,
                        handle = user.handle,
                        host = user.key.host,
                        content = user,
                    ),
                )
            },
            cacheSource = {
                database
                    .userDao()
                    .findByKey(
                        MicroBlogKey(
                            id = id,
                            host = accountKey.host,
                        ),
                    ).distinctUntilChanged()
                    .mapNotNull { it?.content }
            },
        )
}
