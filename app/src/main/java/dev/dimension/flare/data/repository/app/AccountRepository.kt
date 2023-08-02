package dev.dimension.flare.data.repository.app

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
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.repository.app.UiAccount.Companion.toUi
import dev.dimension.flare.data.repository.cache.mastodonUserDataPresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

internal suspend inline fun <reified T : UiAccount> getAccountUseCase(
    accountKey: MicroBlogKey,
    appDatabase: AppDatabase = inject()
): T? {
    val account = appDatabase.accountDao().getAccount(accountKey)?.toUi()
    if (account is T) {
        return account
    }
    return null
}

@Composable
internal fun activeAccountPresenter(
    appDatabase: AppDatabase = rememberInject()
): State<UiState<UiAccount>> {
    return remember(appDatabase) {
        appDatabase.accountDao()
            .getActiveAccount()
            .map {
                it?.toUi()
            }
            .map {
                if (it == null) {
                    UiState.Error(NoActiveAccountException)
                } else {
                    UiState.Success(it)
                } as UiState<UiAccount>
            }
    }.collectAsState(initial = UiState.Loading())
}

@Composable
internal fun allAccountsPresenter(
    appDatabase: AppDatabase = rememberInject()
): State<UiState<ImmutableList<UiAccount>>> {
    return remember(appDatabase) {
        appDatabase.accountDao()
            .getAllAccounts()
            .map {
                it.map { dbAccount ->
                    dbAccount.toUi()
                }
            }
            .map {
                UiState.Success(it.toImmutableList())
            }
    }.collectAsState(initial = UiState.Loading())
}

internal object NoActiveAccountException : Throwable("No active account")

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
            accessToken = accessToken
        ).encodeJson(),
        platform_type = PlatformType.Mastodon,
        lastActive = Clock.System.now().toEpochMilliseconds()
    )
    appDatabase.accountDao().addAccount(account)
}

internal suspend fun setActiveAccountUseCase(
    accountKey: MicroBlogKey,
    appDatabase: AppDatabase = inject()
) {
    appDatabase.accountDao().setActiveAccount(accountKey, Clock.System.now().toEpochMilliseconds())
}

@Composable
internal fun accountDataPresenter(account: UiAccount): UiState<UiUser> {
    return when (account) {
        is UiAccount.Mastodon -> mastodonUserDataPresenter(account = account).toUi()
    }
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
            val accessToken: String
        )

        val service by lazy {
            MastodonService(
                baseUrl = "https://${credential.instance}/",
                accessToken = credential.accessToken
            )
        }
    }

    companion object {
        fun DbAccount.toUi(): UiAccount = when (platform_type) {
            PlatformType.Mastodon -> {
                val credential = credential_json.decodeJson<Mastodon.Credential>()
                Mastodon(
                    credential = credential,
                    accountKey = account_key
                )
            }

            PlatformType.Misskey -> TODO()
        }
    }
}
