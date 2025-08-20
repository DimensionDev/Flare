import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.ZipFile

fun Project.installSqliteJni(
    version: String = "2.5.2",
    osArch: String = "osx_arm64",
    destDir: File =
        project.layout.projectDirectory
            .dir("resources")
            .asFile,
) {
    val baseUrl =
        "https://dl.google.com/android/maven2/androidx/sqlite/sqlite-bundled-jvm/$version"
    val jarName = "sqlite-bundled-jvm-$version.jar"
    val jarUrl = "$baseUrl/$jarName"

    val workDir =
        layout.buildDirectory
            .dir("sqliteJni/$version")
            .get()
            .asFile
    val jarFile = workDir.resolve(jarName)
    val destFile = destDir.resolve("libsqliteJni.dylib")

    val taskName = "installSqliteJni_$version"
    tasks.register(taskName) {
        group = "setup"
        description = "Download $jarName and copy natives/$osArch/libsqliteJni.dylib to resources."

        inputs.property("version", version)
        inputs.property("osArch", osArch)
        outputs.file(destFile)

        doLast {
            workDir.mkdirs()
            destDir.mkdirs()

            if (!jarFile.exists()) {
                logger.lifecycle("Downloading $jarUrl …")
                URL(jarUrl).openStream().use { input ->
                    Files.copy(input, jarFile.toPath(), REPLACE_EXISTING)
                }
            } else {
                logger.lifecycle("Using cached ${jarFile.absolutePath}")
            }

            val entryPath = "natives/$osArch/libsqliteJni.dylib"
            ZipFile(jarFile).use { zip ->
                val entry =
                    zip.getEntry(entryPath)
                        ?: throw GradleException("Entry not found in jar: $entryPath")
                zip.getInputStream(entry).use { zin ->
                    Files.copy(zin, destFile.toPath(), REPLACE_EXISTING)
                }
            }
            logger.lifecycle("Copied -> ${destFile.relativeTo(projectDir)}")
        }
    }

    tasks.named("compileKotlin").configure { dependsOn(taskName) }
}

val v = (findProperty("sqliteVersion") as String?) ?: "2.5.2"
val arch = (findProperty("sqliteOsArch") as String?) ?: "osx_arm64"

installSqliteJni(version = v, osArch = arch, destDir = file("resources"))
