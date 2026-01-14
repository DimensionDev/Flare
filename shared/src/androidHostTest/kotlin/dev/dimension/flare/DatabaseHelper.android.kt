package dev.dimension.flare

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Ignore

internal actual inline fun <reified T : RoomDatabase> Room.memoryDatabaseBuilder(): RoomDatabase.Builder<T> =
    Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().context,
    )

@RunWith(RobolectricTestRunner::class)
@Ignore
actual open class RobolectricTest actual constructor()