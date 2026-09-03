package dev.dimension.flare

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.reflect.KClass
import kotlin.test.Ignore

internal actual fun <T : RoomDatabase> Room.memoryDatabaseBuilder(databaseClass: KClass<T>): RoomDatabase.Builder<T> =
    Room
        .inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            databaseClass.java,
        )

@RunWith(RobolectricTestRunner::class)
@Ignore
actual open class RobolectricTest actual constructor()
