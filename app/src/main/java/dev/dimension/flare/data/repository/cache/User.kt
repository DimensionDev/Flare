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
