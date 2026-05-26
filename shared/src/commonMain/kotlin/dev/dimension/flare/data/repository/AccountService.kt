package dev.dimension.flare.data.repository

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

public interface AccountService {
    public fun accountServiceFlow(accountType: AccountType): Flow<MicroblogDataSource>

    public fun <T : Any> addAccount(
        account: UiAccount,
        credential: T,
        serializer: KSerializer<T>,
    ): Job
}

public inline fun <reified T : Any> AccountService.addAccount(
    account: UiAccount,
    credential: T,
): Job =
    addAccount(
        account = account,
        credential = credential,
        serializer = serializer(),
    )

internal class RepositoryAccountService(
    private val repository: AccountRepository,
) : AccountService {
    override fun accountServiceFlow(accountType: AccountType): Flow<MicroblogDataSource> =
        accountServiceFlow(
            accountType = accountType,
            repository = repository,
        )

    override fun <T : Any> addAccount(
        account: UiAccount,
        credential: T,
        serializer: KSerializer<T>,
    ): Job =
        repository.addAccount(
            account = account,
            credential = credential,
            serializer = serializer,
        )
}
