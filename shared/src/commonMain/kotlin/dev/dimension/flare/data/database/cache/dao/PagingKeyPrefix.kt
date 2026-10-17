package dev.dimension.flare.data.database.cache.dao

// Binding the complete pattern lets SQLite seek the literal namespace prefix using an index range.
internal fun String.toPagingKeyPrefixGlob(): String = replace("[", "[[]").replace("*", "[*]").replace("?", "[?]") + "*"
