package dev.dimension.flare.data.database.app

import app.cash.sqldelight.EnumColumnAdapter
import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton
import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.database.adapter.MicroblogKeyAdapter


@Singleton
@Provides
fun provideAppDatabase(
    driverFactory: DriverFactory,
): AppDatabase {
    val driver = driverFactory.createDriver(AppDatabase.Schema, "app.db")
    return AppDatabase(
        driver,
        DbAccountAdapter = DbAccount.Adapter(
            account_keyAdapter = MicroblogKeyAdapter(),
            platform_typeAdapter = EnumColumnAdapter()
        ),
        DbApplicationAdapter = DbApplication.Adapter(
            platform_typeAdapter = EnumColumnAdapter()
        )
    )
}

