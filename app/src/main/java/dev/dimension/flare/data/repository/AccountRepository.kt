package dev.dimension.flare.data.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.moriatsushi.koject.compose.rememberInject
import com.moriatsushi.koject.inject
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.repository.UiAccount.Companion.toUi
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.collectAsUiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse

@Composable
internal fun activeAccountPresenter(
    appDatabase: AppDatabase = rememberInject(),
): State<UiState<UiAccount?>> {
    return remember(appDatabase) {
        appDatabase.accountDao()
            .getActiveAccount()
            .map { it?.toUi() }
    }.collectAsUiState()
}

@Composable
internal fun mastodonUserDataPresenter(
    account: UiAccount.Mastodon,
    accountKey: MicroBlogKey = account.accountKey,
    cacheDatabase: CacheDatabase = inject(),
): State<UiState<UiUser>> {
    return remember(accountKey) {
        StoreBuilder
            .from<MicroBlogKey, DbUser, DbUser>(
                fetcher = Fetcher.of {
                    account.service.lookupUser(it.id).toDbUser()
                },
                sourceOfTruth = SourceOfTruth.of(
                    reader = {
                        cacheDatabase.userDao().getUser(it)
                    },
                    writer = { key, user ->
                        cacheDatabase.userDao().insertAll(listOf(user))
                    },
                )
            ).build()
            .stream(StoreReadRequest.cached(accountKey, refresh = true))
            .map {
                when (it) {
                    is StoreReadResponse.Data -> UiState.Success(it.value.toUi())
                    is StoreReadResponse.Error.Exception -> UiState.Error(it.error)
                    is StoreReadResponse.Error.Message -> UiState.Error(Exception(it.message))
                    is StoreReadResponse.Loading -> UiState.Loading()
                    is StoreReadResponse.NoNewData -> UiState.Loading()
                } as UiState<UiUser>
            }
    }.collectAsState(initial = UiState.Loading())
}

internal suspend fun addMastodonAccountUseCase(
    instance: String,
    accessToken: String,
    accountKey: MicroBlogKey,
    appDatabase: AppDatabase = inject()
) {
    val account = DbAccount(
        account_key = accountKey,
        credential_json = UiAccount.Mastodon.Credential(
            instance = instance,
            accessToken = accessToken,
        ).encodeJson(),
        platform_type = PlatformType.Mastodon,
        lastActive = Clock.System.now().toEpochMilliseconds(),
    )
    appDatabase.accountDao().addAccount(account)
}

sealed interface UiAccount {
    val accountKey: MicroBlogKey

    data class Mastodon(
        val credential: Credential,
        override val accountKey: MicroBlogKey
    ) : UiAccount {
        @Serializable
        data class Credential(
            val instance: String,
            val accessToken: String,
        )

        val service by lazy {
            MastodonService(
                baseUrl = "https://${credential.instance}/",
                accessToken = credential.accessToken,
            )
        }
    }

    companion object {
        fun DbAccount.toUi(): UiAccount = when (platform_type) {
            PlatformType.Mastodon -> {
                val credential = credential_json.decodeJson<Mastodon.Credential>()
                Mastodon(
                    credential = credential,
                    accountKey = account_key,
                )
            }

            PlatformType.Misskey -> TODO()
        }
    }
}