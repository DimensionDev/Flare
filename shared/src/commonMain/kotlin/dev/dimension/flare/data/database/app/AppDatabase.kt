package dev.dimension.flare.data.database.app

import app.cash.sqldelight.EnumColumnAdapter
import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.database.adapter.MicroblogKeyAdapter
import dev.dimension.flare.data.database.version.VersionDatabase
import dev.dimension.flare.data.version.DbVersion


@Singleton
@Provides
fun provideVersionDatabase(driverFactory: DriverFactory): VersionDatabase {
    return VersionDatabase(
        driverFactory.createDriver(VersionDatabase.Schema, "version.db"),
    )
}

@Singleton
@Provides
fun provideAppDatabase(
    driverFactory: DriverFactory,
    versionDatabase: VersionDatabase,
): AppDatabase {
    val data = versionDatabase.versionQueries.find(0).executeAsOneOrNull()
    val driver = driverFactory.createDriver(AppDatabase.Schema, "app.db")
    if (data != null) {
        val version = data.version
        if (version != AppDatabase.Schema.version) {
            AppDatabase.Schema.migrate(driver, version, AppDatabase.Schema.version)
            versionDatabase.versionQueries.insert(DbVersion(0, AppDatabase.Schema.version))
        }
    } else {
        AppDatabase.Schema.create(driver)
        versionDatabase.versionQueries.insert(DbVersion(0, AppDatabase.Schema.version))
    }
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


//@Singleton
//@Provides
//fun provideCacheDatabase(
//    driverFactory: DriverFactory,
//    versionDb: VersionDatabase,
//): CacheDatabase {
//    val data = versionDb.versionQueries.find(1).executeAsOneOrNull()
//    val driver = driverFactory.createDriver(CacheDatabase.Schema, "cache.db")
//    if (data != null) {
//        val version = data.version.toInt()
//        if (version != CacheDatabase.Schema.version) {
//            val tables = mutableListOf<String>()
//            driver.executeQuery(
//                null,
//                "SELECT name FROM sqlite_master WHERE type='table';",
//                {
//                    while (it.next()) {
//                        it.getString(0)?.let { it1 -> tables.add(it1) }
//                    }
//                },
//                0,
//            )
//            for (table in tables) {
//                if (table == "sqlite_sequence") continue
//                driver.execute(null, "DROP TABLE $table", 0)
//            }
//
//            val views = mutableListOf<String>()
//            driver.executeQuery(
//                null,
//                "SELECT name FROM sqlite_master WHERE type='view';",
//                {
//                    while (it.next()) {
//                        it.getString(0)?.let { it1 -> views.add(it1) }
//                    }
//                },
//                0,
//            )
//            for (view in views) {
//                driver.execute(null, "DROP VIEW $view", 0)
//            }
//
//            CacheDatabase.Schema.create(driver)
//            versionDb.versionQueries.insert(DbVersion(1, CacheDatabase.Schema.version.toLong()))
//        }
//    } else {
//        CacheDatabase.Schema.create(driver)
//        versionDb.versionQueries.insert(DbVersion(1, CacheDatabase.Schema.version.toLong()))
//    }
//    return CacheDatabase(
//        driver,
//        DbStatusAdapter = DbStatusAdapterFactory.create(),
//        DbUserAdapter = DbUserAdapterFactory.create(),
//        DbStatusReactionsAdapter = DbStatusReactionsAdapterFactory.create(),
//        DbTimelineAdapter = DbTimelineAdapterFactory.create(),
//        DbUserRelationAdapter = DbUserRelationAdapterFactory.create(),
//        DbUserListAdapter = DbUserListAdapterFactory.create(),
//        DbWalletAdapter = DbWalletAdapterFactory.create(),
//        DbUserProfileReferenceAdapter = DbUserProfileReferenceAdapter.create(),
//        DbUserWalletAdapter = DbUserWalletAdapterFactory.create(),
//        DbPfpInfoAdapter = DbPfpInfoAdapterFactory.create(),
//        DbTweetExtraAdapter = DbTweetExtraAdapterFactory.create(),
//        DbLinkPreviewExtraAdapter = DbLinkPreviewExtraAdapterFactory.create(),
//    )
//}
