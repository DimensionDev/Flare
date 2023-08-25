package dev.dimension.flare.data.repository.cache

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.bsky.actor.GetProfileQueryParams
import com.moriatsushi.koject.inject
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.CacheableState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.network.bluesky.getService
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.coroutines.flow.mapNotNull
import sh.christian.ozone.api.AtIdentifier

@Composable
internal fun mastodonUserDataPresenter(
    account: UiAccount.Mastodon,
    userId: String = account.accountKey.id,
    cacheDatabase: CacheDatabase = inject()
): CacheableState<UiUser> {
    return remember(account.accountKey, userId) {
        val userKey = MicroBlogKey(userId, account.accountKey.host)
        Cacheable(
            fetchSource = {
                val user = account.service.lookupUser(userId).toDbUser(account.accountKey.host)
                cacheDatabase.userDao().insertAll(listOf(user))
            },
            cacheSource = {
                cacheDatabase.userDao().getUser(userKey)
                    .mapNotNull { it?.toUi() }
            }
        )
    }.collectAsState()
}

@Composable
internal fun mastodonUserDataByNameAndHostPresenter(
    account: UiAccount.Mastodon,
    name: String,
    host: String,
    cacheDatabase: CacheDatabase = inject()
): CacheableState<UiUser> {
    return remember(account.accountKey, name, host) {
        Cacheable(
            fetchSource = {
                val user = account.service.lookupUserByAcct("$name@$host")
                    ?.toDbUser(account.accountKey.host) ?: throw Exception("User not found")
                cacheDatabase.userDao().insertAll(listOf(user))
            },
            cacheSource = {
                cacheDatabase.userDao().getUserByHandleAndHost(name, host, PlatformType.Mastodon)
                    .mapNotNull { it?.toUi() }
            }
        )
    }.collectAsState()
}

@Composable
internal fun misskeyUserDataPresenter(
    account: UiAccount.Misskey,
    userId: String = account.accountKey.id,
    cacheDatabase: CacheDatabase = inject()
): CacheableState<UiUser> {
    return remember(account.accountKey, userId) {
        val userKey = MicroBlogKey(userId, account.accountKey.host)
        Cacheable(
            fetchSource = {
                val user = account
                    .service
                    .findUserById(userId)
                    ?.toDbUser(account.accountKey.host)
                    ?: throw Exception("User not found")
                cacheDatabase.userDao().insertAll(listOf(user))
            },
            cacheSource = {
                cacheDatabase.userDao().getUser(userKey)
                    .mapNotNull { it?.toUi() }
            }
        )
    }.collectAsState()
}

@Composable
internal fun misskeyUserDataByNamePresenter(
    account: UiAccount.Misskey,
    name: String,
    host: String,
    cacheDatabase: CacheDatabase = inject()
): CacheableState<UiUser> {
    return remember(account.accountKey, name) {
        Cacheable(
            fetchSource = {
                val user = account
                    .service
                    .findUserByName(name, host)
                    ?.toDbUser(account.accountKey.host)
                    ?: throw Exception("User not found")
                cacheDatabase.userDao().insertAll(listOf(user))
            },
            cacheSource = {
                cacheDatabase.userDao().getUserByHandleAndHost(name, host, PlatformType.Misskey)
                    .mapNotNull { it?.toUi() }
            }
        )
    }.collectAsState()
}

@Composable
internal fun blueskyUserDataPresenter(
    account: UiAccount.Bluesky,
    userId: String = account.accountKey.id,
    cacheDatabase: CacheDatabase = inject()
) = remember(account.accountKey, userId) {
    Cacheable(
        fetchSource = {
            val user = account.getService()
                .getProfile(GetProfileQueryParams(actor = AtIdentifier(atIdentifier = userId)))
                .requireResponse()
                .toDbUser(account.accountKey.host)
            cacheDatabase.userDao().insertAll(listOf(user))
        },
        cacheSource = {
            cacheDatabase.userDao().getUser(MicroBlogKey(userId, account.accountKey.host))
                .mapNotNull { it?.toUi() }
        }
    )
}.collectAsState()

@Composable
internal fun blueskyUserDataByNamePresenter(
    account: UiAccount.Bluesky,
    name: String,
    host: String,
    cacheDatabase: CacheDatabase = inject()
) = remember(account.accountKey, name) {
    Cacheable(
        fetchSource = {
            val user = account.getService()
                .getProfile(GetProfileQueryParams(actor = AtIdentifier(atIdentifier = name)))
                .requireResponse()
                .toDbUser(account.accountKey.host)
            cacheDatabase.userDao().insertAll(listOf(user))
        },
        cacheSource = {
            cacheDatabase.userDao().getUserByHandleAndHost(name, host, PlatformType.Bluesky)
                .mapNotNull { it?.toUi() }
        }
    )
}.collectAsState()
