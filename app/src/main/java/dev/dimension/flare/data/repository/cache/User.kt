package dev.dimension.flare.data.repository.cache

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton
import com.moriatsushi.koject.inject
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.data.repository.app.getAccountUseCase
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.mobilenativefoundation.store.cache5.CacheBuilder
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse

@Singleton
@Provides
internal fun provideCacheStore(
    cacheDatabase: CacheDatabase = inject(),
    scope: CoroutineScope = inject()
): CacheStore {
    val userStore = StoreBuilder
        .from<UserStoreRequest, DbUser, UiUser>(
            fetcher = Fetcher.of {
                val account = getAccountUseCase<UiAccount.Mastodon>(it.accountKey)
                    ?: throw Exception("Account not found")
                account.service.lookupUser(it.userId).toDbUser(it.accountKey.host)
            },
            sourceOfTruth = SourceOfTruth.of(
                reader = {
                    cacheDatabase.userDao().getUser(MicroBlogKey(it.userId, it.accountKey.host))
                        .map { it?.toUi() }
                },
                writer = { _, user ->
                    cacheDatabase.userDao().insertAll(listOf(user))
                }
            ),
            memoryCache = CacheBuilder<UserStoreRequest, UiUser>()
                .build()
        )
        .scope(scope)
        .build()
    return CacheStore(userStore)
}

internal data class UserStoreRequest(
    val userId: String,
    val accountKey: MicroBlogKey
)

internal data class CacheStore(
    val userStore: Store<UserStoreRequest, UiUser>
)

@Composable
internal fun mastodonUserDataPresenter(
    account: UiAccount.Mastodon,
    userId: String = account.accountKey.id,
    cacheStore: CacheStore = inject()
): State<UiState<UiUser>> {
    return remember(account.accountKey, userId) {
        cacheStore
            .userStore
            .stream(
                StoreReadRequest.cached(
                    UserStoreRequest(
                        userId = userId,
                        accountKey = account.accountKey
                    ),
                    refresh = true
                )
            )
            .map {
                when (it) {
                    is StoreReadResponse.Data -> UiState.Success(it.value)
                    is StoreReadResponse.Error.Exception -> UiState.Error(it.error)
                    is StoreReadResponse.Error.Message -> UiState.Error(Exception(it.message))
                    is StoreReadResponse.Loading -> UiState.Loading()
                    is StoreReadResponse.NoNewData -> UiState.Loading()
                } as UiState<UiUser>
            }
    }.collectAsState(initial = UiState.Loading())
}
