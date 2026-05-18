package dev.dimension.flare.data.database.cache

import androidx.room3.RoomDatabase
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection

public suspend fun <R> RoomDatabase.connect(block: suspend () -> R): R =
    useWriterConnection {
        it.immediateTransaction {
            block.invoke()
        }
    }
