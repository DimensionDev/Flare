import dev.dimension.flare.rust.InitConfig
import dev.dimension.flare.rust.InitError
import dev.dimension.flare.rust.initialize
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>): Unit = runBlocking {
    check(System.getenv("CFFIXED_USER_HOME") == args[0])
    val root = Path.of(args[0], ".flare")
    check(!Files.exists(root))
    val fixture = Files.readAllBytes(Path.of(args[1]))
    val config = InitConfig(androidFilesDir = null)
    try {
        initialize(InitConfig(androidFilesDir = "/android-only"))
        error("invalid configuration succeeded")
    } catch (_: InitError.InvalidConfiguration) {}
    Files.createDirectories(root.parent)
    Files.writeString(root, "blocked directory")
    try {
        initialize(config)
        error("unavailable directory succeeded")
    } catch (_: InitError.DataStore) {}
    check(Files.readString(root) == "blocked directory")
    Files.delete(root)
    Files.createDirectory(root)
    val settings = root.resolve("app_settings.pb")
    Files.write(settings, fixture)

    val cancelled = launch(start = CoroutineStart.UNDISPATCHED) { initialize(config) }
    cancelled.cancelAndJoin()
    coroutineScope { repeat(64) { launch(Dispatchers.Default) { initialize(config) } } }
    initialize(config)
    check(Files.readAllBytes(settings).contentEquals(fixture))
    println("Kotlin initialization: errors, retry, cancellation, concurrency and legacy data passed.")
}
