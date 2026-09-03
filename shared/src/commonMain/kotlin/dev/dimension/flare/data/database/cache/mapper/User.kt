package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile

internal fun UiProfile.toDbUser(host: String = this.host ?: key.host) =
    DbUser(
        userKey = key,
        name = name.raw,
        canonicalHandle = handle.canonical,
        host = host,
        content = this,
    )

internal suspend fun CacheDatabase.upsertUser(user: DbUser) {
    upsertUsers(listOf(user))
}

internal suspend fun CacheDatabase.upsertUsers(users: List<DbUser>) {
    if (users.isEmpty()) {
        return
    }
    val distinctUsersByKey = LinkedHashMap<MicroBlogKey, DbUser>(users.size)
    users.forEach { user -> distinctUsersByKey.getOrPut(user.userKey) { user } }
    val existingUsers =
        distinctUsersByKey.keys
            .chunked(SQL_IN_BATCH_SIZE)
            .flatMap { userDao().findByKeysOnce(it) }
            .associateBy { it.userKey }
    val changedUsers =
        distinctUsersByKey.values.mapNotNull { user ->
            val existing = existingUsers[user.userKey]
            val merged = existing?.let { user.mergeWith(it) } ?: user
            if (merged == existing) {
                null
            } else {
                merged
            }
        }
    if (changedUsers.isEmpty()) {
        return
    }
    userDao().insertAll(changedUsers)
}

private const val SQL_IN_BATCH_SIZE = 500

private fun DbUser.mergeWith(existing: DbUser): DbUser =
    copy(
        name = name.ifBlank { existing.name },
        canonicalHandle = canonicalHandle.ifBlank { existing.canonicalHandle },
        host = host.ifBlank { existing.host },
        content = content.mergeWith(existing.content),
    )
