import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Duration
import java.util.zip.ZipFile

val httpClient: HttpClient =
    HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

fun Project.registerExtractFromJarTask(
    taskName: String,
    jarUrl: String,
    cacheSubDir: String,
    jarFileName: String,
    entryPathInJar: String,
    destFile: File,
): TaskProvider<Task> {
    val workDir =
        layout.buildDirectory
            .dir("native-extract/$cacheSubDir")
            .get()
            .asFile
    val jarFile = workDir.resolve(jarFileName)

    return tasks.register(taskName) {
        group = "setup"
        description = "Download $jarFileName and extract $entryPathInJar -> $destFile"

        inputs.property("jarUrl", jarUrl)
        inputs.property("entryPathInJar", entryPathInJar)
        outputs.file(destFile)

        doLast {
            workDir.mkdirs()
            destFile.parentFile.mkdirs()

            if (!jarFile.exists()) {
                logger.lifecycle("Downloading $jarUrl …")
                val request =
                    HttpRequest
                        .newBuilder(URI.create(jarUrl))
                        .GET()
                        .timeout(Duration.ofMinutes(2))
                        .build()

                val tmp = Files.createTempFile(workDir.toPath(), "download-", ".jar")
                val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tmp))
                if (resp.statusCode() !in 200..299) {
                    try {
                        Files.deleteIfExists(tmp)
                    } catch (_: Exception) {
                    }
                    throw GradleException("Failed to download $jarUrl: HTTP ${resp.statusCode()}")
                }
                Files.move(tmp, jarFile.toPath(), REPLACE_EXISTING)
            } else {
                logger.lifecycle("Using cached ${jarFile.absolutePath}")
            }

            val entryPath = entryPathInJar
            ZipFile(jarFile).use { zip ->
                val entry =
                    zip.getEntry(entryPath)
                        ?: throw GradleException("Entry not found in jar: $entryPath")
                zip.getInputStream(entry).use { zin ->
                    Files.copy(zin, destFile.toPath(), REPLACE_EXISTING)
                }
            }
            logger.lifecycle("Extracted -> ${destFile.toPath().toAbsolutePath().normalize()}")
        }
    }
}

val nativeDestDirPath = (findProperty("nativeDestDir") as String?) ?: "resources/macos-arm64"
val nativeDestDir = file(nativeDestDirPath)

val sqliteVersion = (findProperty("sqliteVersion") as String?) ?: "2.5.2"
val sqliteOsArch = (findProperty("sqliteOsArch") as String?) ?: "osx_arm64"

val sqliteJarName = "sqlite-bundled-jvm-$sqliteVersion.jar"
val sqliteJarUrl =
    "https://dl.google.com/android/maven2/androidx/sqlite/sqlite-bundled-jvm/$sqliteVersion/$sqliteJarName"
val sqliteEntry = "natives/$sqliteOsArch/libsqliteJni.dylib"

val sqliteTask =
    registerExtractFromJarTask(
        taskName = "installSqliteJni_$sqliteVersion",
        jarUrl = sqliteJarUrl,
        cacheSubDir = "sqliteJni/$sqliteVersion",
        jarFileName = sqliteJarName,
        entryPathInJar = sqliteEntry,
        destFile = nativeDestDir.resolve("libsqliteJni.dylib"),
    )

val cmpVersion = (findProperty("composeMediaPlayerVersion") as String?) ?: "0.8.1"
val cmpJarName = "composemediaplayer-jvm-$cmpVersion.jar"
val cmpJarUrl =
    "https://repo1.maven.org/maven2/io/github/kdroidfilter/composemediaplayer-jvm/$cmpVersion/$cmpJarName"
val cmpEntry = "darwin-aarch64/libNativeVideoPlayer.dylib"

val cmpTask =
    registerExtractFromJarTask(
        taskName = "installNativeVideoPlayer_$cmpVersion",
        jarUrl = cmpJarUrl,
        cacheSubDir = "composeMediaPlayer/$cmpVersion",
        jarFileName = cmpJarName,
        entryPathInJar = cmpEntry,
        destFile = nativeDestDir.resolve("libNativeVideoPlayer.dylib"),
    )

val installNativeLibs =
    tasks.register("installNativeLibs") {
        group = "setup"
        description = "Install sqliteJni + NativeVideoPlayer dylibs to $nativeDestDirPath"
        dependsOn(sqliteTask, cmpTask)
    }
