package dev.dimension.flare.data.database

import app.cash.sqldelight.EnumColumnAdapter
import dev.dimension.flare.data.cache.DbStatus
import dev.dimension.flare.data.cache.DbUser
import dev.dimension.flare.data.database.adapter.JsonColumnAdapter
import dev.dimension.flare.data.database.adapter.MicroblogKeyAdapter
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.DbAccount
import dev.dimension.flare.data.database.app.DbApplication
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.database.version.VersionDatabase
import dev.dimension.flare.data.version.DbVersion

internal fun provideVersionDatabase(driverFactory: DriverFactory): VersionDatabase {
    return VersionDatabase(
        driverFactory.createDriver(VersionDatabase.Schema, "version.db"),
    )
}

internal fun provideAppDatabase(
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
        DbAccountAdapter =
            DbAccount.Adapter(
                account_keyAdapter = MicroblogKeyAdapter(),
                platform_typeAdapter = EnumColumnAdapter(),
            ),
        DbApplicationAdapter =
            DbApplication.Adapter(
                platform_typeAdapter = EnumColumnAdapter(),
            ),
    )
}

internal fun provideCacheDatabase(
    driverFactory: DriverFactory,
    versionDb: VersionDatabase,
): CacheDatabase {
    val data = versionDb.versionQueries.find(1).executeAsOneOrNull()
    val version = data?.version
    val shouldRecreateDatabase = version != null && version != CacheDatabase.Schema.version
    if (shouldRecreateDatabase) {
        driverFactory.deleteDatabase("cache.db")
    }
    val driver = driverFactory.createDriver(CacheDatabase.Schema, "cache.db")
    if (data == null || shouldRecreateDatabase) {
        CacheDatabase.Schema.create(driver)
        versionDb.versionQueries.insert(DbVersion(1, CacheDatabase.Schema.version))
    }
    return CacheDatabase(
        driver,
        DbStatusAdapter =
            DbStatus.Adapter(
                status_keyAdapter = MicroblogKeyAdapter(),
                platform_typeAdapter = EnumColumnAdapter(),
                account_keyAdapter = MicroblogKeyAdapter(),
                user_keyAdapter = MicroblogKeyAdapter(),
                contentAdapter = JsonColumnAdapter(StatusContent.serializer()),
            ),
        DbUserAdapter =
            DbUser.Adapter(
                user_keyAdapter = MicroblogKeyAdapter(),
                platform_typeAdapter = EnumColumnAdapter(),
                contentAdapter = JsonColumnAdapter(UserContent.serializer()),
            ),
        DbPagingTimelineAdapter =
            dev.dimension.flare.data.cache.DbPagingTimeline.Adapter(
                account_keyAdapter = MicroblogKeyAdapter(),
                status_keyAdapter = MicroblogKeyAdapter(),
            ),
        DbEmojiAdapter =
            dev.dimension.flare.data.cache.DbEmoji.Adapter(
                contentAdapter = JsonColumnAdapter(EmojiContent.serializer()),
            ),
    )
}
