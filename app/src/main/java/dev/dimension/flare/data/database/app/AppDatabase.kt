package dev.dimension.flare.data.database.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton
import dev.dimension.flare.data.database.Converters
import dev.dimension.flare.data.database.app.dao.AccountDao
import dev.dimension.flare.data.database.app.dao.ApplicationDao
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.app.model.DbApplication

@Database(entities = [DbAccount::class, DbApplication::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun applicationDao(): ApplicationDao
}

@Singleton
@Provides
fun provideAppDatabase(
    applicationContext: Context,
): AppDatabase {
    return Room.databaseBuilder(
        applicationContext,
        AppDatabase::class.java,
        "app_database",
    ).build()
}
