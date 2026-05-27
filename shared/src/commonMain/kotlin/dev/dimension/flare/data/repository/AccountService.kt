package dev.dimension.flare.data.repository

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.koin.core.annotation.Single

public interface AccountService {
    public fun accountServiceFlow(accountType: AccountType): Flow<MicroblogDataSource>

    public fun <T : Any> addAccount(
        account: UiAccount,
        credential: T,
        serializer: KSerializer<T>,
    ): Job

    public fun <T : Any> credentialFlow(
        accountKey: MicroBlogKey,
        serializer: KSerializer<T>,
    ): Flow<T>

    public fun <T : Any> updateCredential(
        accountKey: MicroBlogKey,
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

public inline fun <reified T : Any> AccountService.credentialFlow(accountKey: MicroBlogKey): Flow<T> =
    credentialFlow(
        accountKey = accountKey,
        serializer = serializer(),
    )

public inline fun <reified T : Any> AccountService.updateCredential(
    accountKey: MicroBlogKey,
    credential: T,
): Job =
    updateCredential(
        accountKey = accountKey,
        credential = credential,
        serializer = serializer(),
    )

@Single(binds = [AccountService::class])
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

    override fun <T : Any> credentialFlow(
        accountKey: MicroBlogKey,
        serializer: KSerializer<T>,
    ): Flow<T> =
        repository.credentialFlow(
            accountKey = accountKey,
            serializer = serializer,
        )

    override fun <T : Any> updateCredential(
        accountKey: MicroBlogKey,
        credential: T,
        serializer: KSerializer<T>,
    ): Job =
        repository.updateCredential(
            accountKey = accountKey,
            credential = credential,
            serializer = serializer,
        )
}
