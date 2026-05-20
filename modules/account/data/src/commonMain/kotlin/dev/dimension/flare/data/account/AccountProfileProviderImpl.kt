package dev.dimension.flare.data.account

import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

public class AccountProfileProviderImpl(
    private val accountRepository: AccountRepository,
) : AccountProfileProvider {
    override val accounts: Flow<List<AccountProfileProvider.AccountProfile>> =
        accountRepository
            .allAccounts
            .map { accounts ->
                accounts.map { account ->
                    flow {
                        val service = accountRepository.getOrCreateDataSource(account)
                        if (service is UserDataSource && service is AuthenticatedMicroblogDataSource) {
                            emitAll(
                                service
                                    .userHandler
                                    .userById(account.accountKey.id)
                                    .data
                                    .map { profile ->
                                        AccountProfileProvider.AccountProfile(
                                            account = account,
                                            avatar =
                                                when (profile) {
                                                    is CacheState.Success -> profile.data.avatar
                                                    is CacheState.Empty -> null
                                                },
                                        )
                                    },
                            )
                        } else {
                            emit(
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
