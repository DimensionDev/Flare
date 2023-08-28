package dev.dimension.flare.data.repository.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.moriatsushi.koject.compose.rememberInject
import com.moriatsushi.koject.inject
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.datasource.MicroblogService
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.repository.app.UiAccount.Companion.toUi
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.map
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

internal suspend inline fun <reified T : UiAccount> getAccountUseCase(
    accountKey: MicroBlogKey,
    appDatabase: AppDatabase = inject(),
): T? {
    val account = appDatabase.accountDao().getAccount(accountKey)?.toUi()
    if (account is T) {
        return account
    }
    return null
}

@Composable
internal fun activeAccountPresenter(
    appDatabase: AppDatabase = rememberInject(),
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
internal fun activeAccountServicePresenter(): UiState<Pair<MicroblogService, UiAccount>> {
    val account by activeAccountPresenter()
    return account.map {
        accountServiceProvider(it) to it
    }
}

@Composable
internal fun accountServiceProvider(
    account: UiAccount,
): MicroblogService {
    return remember(account.accountKey) {
        when (account) {
            is UiAccount.Mastodon -> {
                dev.dimension.flare.data.datasource.mastodon.MastodonService(
                    account = account,
                )
            }

            is UiAccount.Misskey -> {
                dev.dimension.flare.data.datasource.misskey.MisskeyService(
                    account = account,
                )
            }

            is UiAccount.Bluesky -> {
                dev.dimension.flare.data.datasource.bluesky.BlueskyService(
                    account = account,
                )
            }
        }
    }
}

@Composable
internal fun allAccountsPresenter(
    appDatabase: AppDatabase = rememberInject(),
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
    appDatabase: AppDatabase = inject(),
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

internal suspend fun addMisskeyAccountUseCase(
    host: String,
    token: String,
    accountKey: MicroBlogKey,
    appDatabase: AppDatabase = inject(),
) {
    val account = DbAccount(
        account_key = accountKey,
        credential_json = UiAccount.Misskey.Credential(
            host = host,
            accessToken = token,
        ).encodeJson(),
        platform_type = PlatformType.Misskey,
        lastActive = Clock.System.now().toEpochMilliseconds(),
    )
    appDatabase.accountDao().addAccount(account)
}

internal suspend fun addBlueskyAccountUseCase(
    baseUrl: String,
    accessToken: String,
    refreshToken: String,
    accountKey: MicroBlogKey,
    appDatabase: AppDatabase = inject(),
) {
    val account = DbAccount(
        account_key = accountKey,
        credential_json = UiAccount.Bluesky.Credential(
            baseUrl = baseUrl,
            accessToken = accessToken,
            refreshToken = refreshToken,
        ).encodeJson(),
        platform_type = PlatformType.Bluesky,
        lastActive = Clock.System.now().toEpochMilliseconds(),
    )
    appDatabase.accountDao().addAccount(account)
}

internal suspend fun updateBlueskyTokenUseCase(
    baseUrl: String,
    accessToken: String,
    refreshToken: String,
    accountKey: MicroBlogKey,
    appDatabase: AppDatabase = inject(),
) {
    appDatabase.accountDao().updateCredentialJson(
        accountKey,
        UiAccount.Bluesky.Credential(
            baseUrl = baseUrl,
            accessToken = accessToken,
            refreshToken = refreshToken,
        ).encodeJson(),
    )
}

internal suspend fun setActiveAccountUseCase(
    accountKey: MicroBlogKey,
    appDatabase: AppDatabase = inject(),
) {
    appDatabase.accountDao().setActiveAccount(accountKey, Clock.System.now().toEpochMilliseconds())
}

sealed interface UiAccount {
    val accountKey: MicroBlogKey

    data class Mastodon(
        val credential: Credential,
        override val accountKey: MicroBlogKey,
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

    data class Misskey(
        val credential: Credential,
        override val accountKey: MicroBlogKey,
    ) : UiAccount {
        @Serializable
        data class Credential(
            val host: String,
            val accessToken: String,
        )

        val service by lazy {
            dev.dimension.flare.data.network.misskey.MisskeyService(
                baseUrl = "https://${credential.host}/api/",
                token = credential.accessToken,
            )
        }
    }

    data class Bluesky(
        val credential: Credential,
        override val accountKey: MicroBlogKey,
    ) : UiAccount {
        @Serializable
        data class Credential(
            val baseUrl: String,
            val accessToken: String,
            val refreshToken: String,
        )
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

            PlatformType.Misskey -> {
                val credential = credential_json.decodeJson<Misskey.Credential>()
                Misskey(
                    credential = credential,
                    accountKey = account_key,
                )
            }

            PlatformType.Bluesky -> {
                val credential = credential_json.decodeJson<Bluesky.Credential>()
                Bluesky(
                    credential = credential,
                    accountKey = account_key,
                )
            }
        }
    }
}
