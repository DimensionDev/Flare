package dev.dimension.flare

import android.os.Looper
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

internal actual fun TestScope.startPlatformEventLoop() {
    // AndroidUiDispatcher bypasses Dispatchers.setMain and posts directly to the main Looper.
    // Keep it running through cancelAndJoin; runTest cancels backgroundScope afterward.
    backgroundScope.launch {
        while (isActive) {
            shadowOf(Looper.getMainLooper()).idle()
            delay(1)
        }
    }
}
