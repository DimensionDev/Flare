package dev.dimension.flare.di

import dev.dimension.flare.data.account.AccountLookup
import dev.dimension.flare.data.account.AccountProfileProvider
import dev.dimension.flare.data.account.AccountProfileProviderImpl
import dev.dimension.flare.data.account.AccountRepository
import dev.dimension.flare.data.account.ApplicationRepository
import dev.dimension.flare.data.account.CredentialProvider
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

public val accountDataModule: Module =
    module {
        singleOf(::AccountRepository)
        single<AccountProfileProvider> { AccountProfileProviderImpl(get()) }
        single<AccountLookup> { get<AccountRepository>() }
        single<CredentialProvider> { get<AccountRepository>() }
        singleOf(::ApplicationRepository)
    }
