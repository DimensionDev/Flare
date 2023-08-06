package dev.dimension.flare.data.repository.cache

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.moriatsushi.koject.inject
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.CacheableState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.coroutines.flow.mapNotNull

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
                cacheDatabase.userDao().getUserByHandleAndHost(name, host)
                    .mapNotNull { it?.toUi() }
            }
        )
    }.collectAsState()
}

@Composable
internal fun misskeyUserDataPresenter(
    account: UiAccount.Misskey,
    userId: String = account.accountKey.id,
    cacheDatabase: CacheDatabase = inject(),
    misskeyEmojiCache: MisskeyEmojiCache = inject()
): CacheableState<UiUser> {
    return remember(account.accountKey, userId) {
        val userKey = MicroBlogKey(userId, account.accountKey.host)
        Cacheable(
            fetchSource = {
                val emojis = misskeyEmojiCache.getEmojis(account)
                val user = account
                    .service
                    .findUserById(userId)
                    ?.toDbUser(account.accountKey.host, emojis)
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
    cacheDatabase: CacheDatabase = inject(),
    misskeyEmojiCache: MisskeyEmojiCache = inject()
): CacheableState<UiUser> {
    return remember(account.accountKey, name) {
        Cacheable(
            fetchSource = {
                val emojis = misskeyEmojiCache.getEmojis(account)
                val user = account
                    .service
                    .findUserByName(name, host)
                    ?.toDbUser(account.accountKey.host, emojis)
                    ?: throw Exception("User not found")
                cacheDatabase.userDao().insertAll(listOf(user))
            },
            cacheSource = {
                cacheDatabase.userDao().getUserByHandleAndHost(name, host)
                    .mapNotNull { it?.toUi() }
            }
        )
    }.collectAsState()
}
