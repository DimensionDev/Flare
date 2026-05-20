package dev.dimension.flare.di

internal fun appModule() =
    listOf(
        platformModule,
        foundationDatabaseModule,
        settingsDataModule,
        accountDataModule,
        draftDataModule,
        localDataModule,
        aiDataModule,
        translationDataModule,
        commonModule,
    )
