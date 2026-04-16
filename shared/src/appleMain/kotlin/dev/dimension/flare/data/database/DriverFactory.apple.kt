package dev.dimension.flare.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

internal const val APP_GROUP_ID = "group.dev.dimension.flare"

private const val MIGRATION_STATE_NONE = "none"
private const val MIGRATION_STATE_COPYING = "copying"
private const val MIGRATION_STATE_COPIED = "copied"
private const val MIGRATION_STATE_FINISHED = "finished"

internal actual class DriverFactory {
    actual inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): RoomDatabase.Builder<T> {
        val dbFilePath =
            if (isCache) {
                "${legacyDatabaseDirPath()}/$name"
            } else {
                "${sharedDatabaseDirPath()}/$name"
            }

        return Room.databaseBuilder<T>(
            name = dbFilePath,
        )
    }

    internal fun legacyDatabaseDirPath(): String = iosAppSupportDirPath("databases")

    @OptIn(ExperimentalForeignApi::class)
    internal fun sharedDatabaseDirPath(): String =
        appGroupDirPath(
            groupId = APP_GROUP_ID,
            folder = "databases",
        )
}

public fun migrateDatabase() {
    prepareRoomMigrationIfNeeded(APP_DATABASE_NAME)
    finalizeRoomMigrationIfNeeded(APP_DATABASE_NAME)
}

@OptIn(ExperimentalForeignApi::class)
private fun prepareRoomMigrationIfNeeded(name: String) {
    val state = getMigrationState(name)
    val oldBasePath = "${iosAppSupportDirPath("databases")}/$name"
    val newBasePath = "${appGroupDirPath(APP_GROUP_ID, "databases")}/$name"

    val oldExists = sqliteFileFamily(oldBasePath).any { fileExists(it) }
    val newExists = sqliteFileFamily(newBasePath).any { fileExists(it) }

    when (state) {
        MIGRATION_STATE_FINISHED -> {
            return
        }

        MIGRATION_STATE_COPIED -> {
            return
        }

        MIGRATION_STATE_COPYING -> {
            deleteSqliteFamily(newBasePath)
            setMigrationState(name, MIGRATION_STATE_NONE)
        }
    }

    if (!oldExists) {
        return
    }

    if (newExists) {
        setMigrationState(name, MIGRATION_STATE_COPIED)
        return
    }

    setMigrationState(name, MIGRATION_STATE_COPYING)
    try {
        copySqliteFamily(
            fromBasePath = oldBasePath,
            toBasePath = newBasePath,
        )
        setMigrationState(name, MIGRATION_STATE_COPIED)
    } catch (t: Throwable) {
        deleteSqliteFamily(newBasePath)
        setMigrationState(name, MIGRATION_STATE_NONE)
        throw t
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun finalizeRoomMigrationIfNeeded(name: String) {
    val state = getMigrationState(name)
    if (state != MIGRATION_STATE_COPIED) return

    val oldBasePath = "${iosAppSupportDirPath("databases")}/$name"
    val newBasePath = "${appGroupDirPath(APP_GROUP_ID, "databases")}/$name"

    if (!fileExists(newBasePath)) return

    deleteSqliteFamily(oldBasePath)
    setMigrationState(name, MIGRATION_STATE_FINISHED)
}

private fun migrationStateKey(name: String): String = "room_migration_state_$name"

@OptIn(ExperimentalForeignApi::class)
private fun getMigrationState(name: String): String {
    val defaults = NSUserDefaults(suiteName = APP_GROUP_ID)
    return defaults.stringForKey(migrationStateKey(name)) ?: MIGRATION_STATE_NONE
}

@OptIn(ExperimentalForeignApi::class)
private fun setMigrationState(
    name: String,
    state: String,
) {
    val defaults = NSUserDefaults(suiteName = APP_GROUP_ID)
    defaults.setObject(state, forKey = migrationStateKey(name))
    defaults.synchronize()
}

private fun sqliteFileFamily(basePath: String): List<String> =
    listOf(
        basePath,
        "$basePath-wal",
        "$basePath-shm",
    )

@OptIn(ExperimentalForeignApi::class)
private fun copySqliteFamily(
    fromBasePath: String,
    toBasePath: String,
) {
    val fileManager = NSFileManager.defaultManager

    deleteSqliteFamily(toBasePath)

    sqliteFileFamily(fromBasePath).zip(sqliteFileFamily(toBasePath)).forEach { (src, dst) ->
        if (fileExists(src)) {
            val ok = fileManager.copyItemAtPath(src, dst, null)
            check(ok) { "Failed to copy $src to $dst" }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun deleteSqliteFamily(basePath: String) {
    val fileManager = NSFileManager.defaultManager
    sqliteFileFamily(basePath).forEach { path ->
        if (fileExists(path)) {
            fileManager.removeItemAtPath(path, null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun fileExists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

@OptIn(ExperimentalForeignApi::class)
internal fun appGroupDirPath(
    groupId: String,
    folder: String,
): String {
    val fileManager = NSFileManager.defaultManager
    val containerUrl: NSURL =
        fileManager
            .containerURLForSecurityApplicationGroupIdentifier(groupId)
            ?: error("App Group not configured: $groupId")

    val dir = "${requireNotNull(containerUrl.path)}/$folder"

    if (!fileManager.fileExistsAtPath(dir)) {
        fileManager.createDirectoryAtPath(dir, true, null, null)
    }

    return dir
}

@OptIn(ExperimentalForeignApi::class)
internal fun iosAppSupportDirPath(folder: String): String {
    val paths =
        NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true,
        )
    val appSupportDirectory = paths[0] as String
    val dir = "$appSupportDirectory/$folder"

    val fileManager = NSFileManager.defaultManager
    if (!fileManager.fileExistsAtPath(dir)) {
        fileManager.createDirectoryAtPath(dir, true, null, null)
    }

    return dir
}
