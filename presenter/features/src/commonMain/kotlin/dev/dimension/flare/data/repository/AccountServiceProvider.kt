package dev.dimension.flare.data.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.takeSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

@Composable
internal fun accountProvider(
    accountType: AccountType,
    repository: AccountRepository,
): State<UiState<UiAccount>> =
    produceState<UiState<UiAccount>>(
        initialValue = UiState.Loading(),
        key1 = accountType,
    ) {
        when (accountType) {
            AccountType.Guest,
            is AccountType.GuestHost,
            -> {
                flowOf(
                    UiState.Error(
                        NoActiveAccountException,
                    ),
                )
            }

            is AccountType.Specific -> {
                repository.getFlow(accountType.accountKey)
            }
        }.collect {
            value = it
        }
    }

@Composable
internal fun accountServiceProvider(
    accountType: AccountType,
    repository: AccountRepository,
): UiState<MicroblogDataSource> =
    remember(
        accountType,
    ) {
        accountServiceFlow(
            accountType = accountType,
            repository = repository,
        )
    }.collectAsUiState().value

@OptIn(ExperimentalCoroutinesApi::class)
internal fun accountServiceFlow(
    accountType: AccountType,
    repository: AccountRepository,
): Flow<MicroblogDataSource> =
    when (accountType) {
        AccountType.Guest -> {
            flowOf(
                repository.guestDataSource(
                    type = PlatformType.Mastodon,
                    host = "mastodon.social",
                    locale = Locale.language,
                ),
            )
        }

        is AccountType.GuestHost -> {
            flowOf(
                repository.guestDataSource(
                    type = PlatformType.Mastodon,
                    host = accountType.host,
                    locale = Locale.language,
                ),
            )
        }

        is AccountType.Specific -> {
            repository
                .getFlow(accountType.accountKey)
                .mapNotNull { it.takeSuccess() }
                .distinctUntilChangedBy { it.accountKey }
                .map { account -> repository.getOrCreateDataSource(account) }
        }
    }

internal fun activeAccountFlow(repository: AccountRepository): Flow<UiAccount?> =
    repository
        .activeAccount
        .map { it.takeSuccess() }
        .distinctUntilChangedBy { it?.accountKey }

internal fun allAccountServicesFlow(repository: AccountRepository): Flow<ImmutableList<MicroblogDataSource>> =
    repository.allAccounts.map { accounts ->
        accounts
            .map {
                repository.getOrCreateDataSource(it)
            }.toImmutableList()
    }
