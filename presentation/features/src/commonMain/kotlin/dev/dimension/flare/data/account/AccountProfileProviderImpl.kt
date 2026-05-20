package dev.dimension.flare.data.account

import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class AccountProfileProviderImpl(
    private val accountRepository: AccountRepository,
) : AccountProfileProvider {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val accounts: Flow<List<AccountProfileProvider.AccountProfile>> =
        accountRepository
            .allAccounts
            .map { accounts ->
                accounts.map { account ->
                    accountServiceFlow(
                        accountType = AccountType.Specific(account.accountKey),
                        repository = accountRepository,
                    ).flatMapLatest { service ->
                        if (service is UserDataSource && service is AuthenticatedMicroblogDataSource) {
                            service
                                .userHandler
                                .userById(account.accountKey.id)
                                .toUi()
                                .map { profile ->
                                    AccountProfileProvider.AccountProfile(
                                        account = account,
                                        avatar = profile.takeSuccess()?.avatar,
                                    )
                                }
                        } else {
                            flowOf(
                                AccountProfileProvider.AccountProfile(
                                    account = account,
                                    avatar = null,
                                ),
                            )
                        }
                    }
                }
            }.combineLatestFlowLists()
}
