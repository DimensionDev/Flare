package dev.dimension.flare

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Ignore
import kotlin.reflect.KClass

internal actual fun <T : RoomDatabase> Room.memoryDatabaseBuilder(databaseClass: KClass<T>): RoomDatabase.Builder<T> =
    Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().context,
        databaseClass.java,
    )

@RunWith(RobolectricTestRunner::class)
@Ignore
actual open class RobolectricTest actual constructor()
