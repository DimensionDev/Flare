package dev.dimension.flare.di

import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
internal class NostrTestKoinModule {
    @Single
    fun coroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.IO)

    @Single
    fun accountService(): AccountService = TestAccountService

    @Single
    fun inAppNotification(): InAppNotification = TestInAppNotification
}

private object TestInAppNotification : InAppNotification {
    override fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    ) = Unit

    override fun onSuccess(message: Message) = Unit

    override fun onError(
        message: Message,
        throwable: Throwable,
    ) = Unit
}

private object TestAccountService : AccountService {
    override fun accountServiceFlow(accountType: AccountType): Flow<MicroblogDataSource> = unsupported()

    override fun allAccountServicesFlow(): Flow<List<AccountMicroblogDataSource>> {
        unsupported()
    }

    override fun <T : Any> addAccount(
        account: UiAccount,
        credential: T,
        serializer: KSerializer<T>,
    ): Job = unsupported()

    override fun <T : Any> credentialFlow(
        accountKey: MicroBlogKey,
        serializer: KSerializer<T>,
    ): Flow<T> = unsupported()

    override fun <T : Any> updateCredential(
        accountKey: MicroBlogKey,
        credential: T,
        serializer: KSerializer<T>,
    ): Job = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Test AccountService is only used for Koin compile safety")
}
