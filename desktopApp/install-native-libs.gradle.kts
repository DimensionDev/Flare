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

val currentArch = System.getProperty("os.arch")
val isArm = currentArch == "aarch64" || currentArch == "arm64"
val isX64 = currentArch == "x86_64" || currentArch == "amd64"
val currentOs =
    org.gradle.internal.os.OperatingSystem
        .current()

val resourceDirName =
    when {
        currentOs.isMacOsX && isArm -> "macos-arm64"
        currentOs.isMacOsX && isX64 -> "macos-x64"
        currentOs.isLinux && isArm -> "linux-arm64"
        currentOs.isLinux && isX64 -> "linux-x64"
        currentOs.isWindows && isX64 -> "windows-x64"
        currentOs.isWindows && isArm -> "windows-arm64"
        else -> throw GradleException("Unsupported OS or architecture: ${currentOs.name} $currentArch")
    }

val nativeDestDirPath = "resources/$resourceDirName"
val nativeDestDir = file(nativeDestDirPath)
val sqliteVersion = (findProperty("sqliteVersion") as String?) ?: "2.5.2"
val sqliteOsArch =
    when {
        currentOs.isMacOsX && isArm -> "osx_arm64"
        currentOs.isMacOsX && isX64 -> "osx_x64"
        currentOs.isLinux && isArm -> "linux_arm64"
        currentOs.isLinux && isX64 -> "linux_x64"
        currentOs.isWindows && isX64 -> "windows_x64"
        currentOs.isWindows && isArm -> "windows_arm64"
        else -> throw GradleException("Unsupported OS or architecture: ${currentOs.name} $currentArch")
    }

val sqliteFileName =
    when {
        currentOs.isMacOsX -> "libsqliteJni.dylib"
        currentOs.isLinux -> "libsqliteJni.so"
        currentOs.isWindows -> "sqliteJni.dll"
        else -> throw GradleException("Unsupported OS: ${currentOs.name}")
    }

val sqliteJarName = "sqlite-bundled-jvm-$sqliteVersion.jar"
val sqliteJarUrl =
    "https://dl.google.com/android/maven2/androidx/sqlite/sqlite-bundled-jvm/$sqliteVersion/$sqliteJarName"
val sqliteEntry = "natives/$sqliteOsArch/$sqliteFileName"

val sqliteTask =
    registerExtractFromJarTask(
        taskName = "installSqliteJni_$sqliteVersion",
        jarUrl = sqliteJarUrl,
        cacheSubDir = "sqliteJni/$sqliteVersion",
        jarFileName = sqliteJarName,
        entryPathInJar = sqliteEntry,
        destFile = nativeDestDir.resolve(sqliteFileName),
    )
val jnaVersion = (findProperty("jnaVersion") as String?) ?: "5.17.0"
val jnaJarName = "jna-$jnaVersion.jar"
val jnaJarUrl = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/$jnaVersion/$jnaJarName"
val jnaDirName =
    when {
        currentOs.isMacOsX && isArm -> "darwin-aarch64"
        currentOs.isMacOsX && isX64 -> "darwin-x86-64"
        currentOs.isLinux && isArm -> "linux-aarch64"
        currentOs.isLinux && isX64 -> "linux-x86-64"
        currentOs.isWindows && isX64 -> "win32-x86-64"
        currentOs.isWindows && isArm -> "win32-arm64"
        else -> throw GradleException("Unsupported OS or architecture: ${currentOs.name} $currentArch")
    }
val jnaLibName =
    when {
        currentOs.isMacOsX -> "libjnidispatch.jnilib"
        currentOs.isLinux -> "libjnidispatch.so"
        currentOs.isWindows -> "jnidispatch.dll"
        else -> throw GradleException("Unsupported OS: ${currentOs.name}")
    }
val jnaEntry = "com/sun/jna/$jnaDirName/$jnaLibName"
val targetJnaLibName =
    when {
        currentOs.isMacOsX -> "libjnidispatch.dylib"
        else -> jnaLibName
    }

val jnaTask =
    registerExtractFromJarTask(
        taskName = "installJnaNative_$jnaVersion",
        jarUrl = jnaJarUrl,
        cacheSubDir = "jna/$jnaVersion",
        jarFileName = jnaJarName,
        entryPathInJar = jnaEntry,
        destFile = nativeDestDir.resolve(targetJnaLibName),
    )

afterEvaluate {
    tasks.named("compileKotlin").configure {
        dependsOn(sqliteTask, jnaTask)
    }
    tasks.named("prepareAppResources").configure {
        dependsOn(sqliteTask, jnaTask)
    }
}
