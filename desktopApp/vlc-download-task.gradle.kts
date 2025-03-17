import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

// Define task class
open class DownloadVlcTask : DefaultTask() {
    // Set VLC version as a property for easy updates
    private val vlcVersion = "3.0.20"

    @TaskAction
    fun downloadVlc() {
        val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
        val isMacOS = Os.isFamily(Os.FAMILY_MAC)
        val isArm64 =
            System.getProperty("os.arch").lowercase().contains("aarch64") ||
                System.getProperty("os.arch").lowercase().contains("arm64")

        // Use projectDir to locate resource directory
        val resourcesDir = File(project.projectDir, "resources")
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs()
        }

        // Create download directory in project's build folder
        val downloadDir = File(project.buildDir, "download")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        when {
            isWindows -> {
                val targetDir = File(resourcesDir, "windows-x64")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                // Check if directory is empty
                if (targetDir.listFiles()?.isEmpty() != false) {
                    println("Windows x64 platform detected, downloading VLC $vlcVersion...")
                    downloadAndExtractZip(
                        "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip",
                        targetDir,
                        downloadDir,
                    )
                } else {
                    println("Windows x64 directory already contains files, skipping download")
                }
            }

            isMacOS && isArm64 -> {
                val targetDir = File(resourcesDir, "macos-arm64")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                // Check if directory is empty
                if (targetDir.listFiles()?.isEmpty() != false) {
                    println("MacOS ARM64 platform detected, downloading VLC $vlcVersion...")
                    downloadAndMountDmg(
                        "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/macosx/vlc-$vlcVersion-arm64.dmg",
                        targetDir,
                        downloadDir,
                    )
                } else {
                    println("MacOS ARM64 directory already contains files, skipping download")
                }
            }

            isMacOS -> {
                val targetDir = File(resourcesDir, "macos-x64")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                // Check if directory is empty
                if (targetDir.listFiles()?.isEmpty() != false) {
                    println("MacOS x64 platform detected, downloading VLC $vlcVersion...")
                    downloadAndMountDmg(
                        "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/macosx/vlc-$vlcVersion-intel64.dmg",
                        targetDir,
                        downloadDir,
                    )
                } else {
                    println("MacOS x64 directory already contains files, skipping download")
                }
            }

            else -> {
                println("Unsupported platform")
            }
        }
    }

    private fun downloadAndExtractZip(
        url: String,
        targetDir: File,
        downloadDir: File,
    ) {
        try {
            println("Downloading: $url")
            val tempFile = File(downloadDir, "vlc-download-${System.currentTimeMillis()}.zip")

            // Use URI and HttpClient instead of deprecated URL constructor
            val client = HttpClient.newBuilder().build()
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build()

            client.send(request, HttpResponse.BodyHandlers.ofInputStream()).body().use { input ->
                Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            println("Download complete, extracting...")

            // Create lib directory
            val libDir = File(targetDir, "lib")
            if (!libDir.exists()) {
                libDir.mkdirs()
            }

            // First pass: determine the root directory in the zip
            var rootDirName = ""
            ZipInputStream(tempFile.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                if (entry != null) {
                    val pathParts = entry.name.split("/")
                    if (pathParts.isNotEmpty()) {
                        rootDirName = pathParts[0]
                    }
                }
            }

            println("Detected root directory in zip: $rootDirName")

            // Second pass: extract files, renaming from 'vlc-x.x.x' to 'lib'
            ZipInputStream(tempFile.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    // Skip the root directory entry itself
                    if (entry.name != "$rootDirName/") {
                        // Replace the root directory name with "lib"
                        val relativePath =
                            if (rootDirName.isNotEmpty()) {
                                entry.name.substring(rootDirName.length + 1)
                            } else {
                                entry.name
                            }

                        val outputFile = File(libDir, relativePath)

                        if (entry.isDirectory) {
                            if (!outputFile.exists()) {
                                outputFile.mkdirs()
                            }
                        } else {
                            // Create parent directories if they don't exist
                            outputFile.parentFile?.mkdirs()

                            // Extract file
                            outputFile.outputStream().use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                    }

                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            println("Extraction complete")
        } catch (e: Exception) {
            println("Error during download or extraction: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun downloadAndMountDmg(
        url: String,
        targetDir: File,
        downloadDir: File,
    ) {
        try {
            println("Downloading: $url")
            val tempFile = File(downloadDir, "vlc-download-${System.currentTimeMillis()}.dmg")

            // Use URI and HttpClient instead of deprecated URL constructor
            val client = HttpClient.newBuilder().build()
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build()

            client.send(request, HttpResponse.BodyHandlers.ofInputStream()).body().use { input ->
                Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            println("Download complete, mounting DMG file...")

            // Mount DMG file
            val mountPoint = File(System.getProperty("java.io.tmpdir"), "vlc_mount")
            if (!mountPoint.exists()) {
                mountPoint.mkdirs()
            }

            val mountProcess =
                ProcessBuilder(
                    "hdiutil",
                    "attach",
                    "-mountpoint",
                    mountPoint.absolutePath,
                    tempFile.absolutePath,
                ).start()

            val mountExitCode = mountProcess.waitFor()
            if (mountExitCode != 0) {
                throw RuntimeException("Failed to mount DMG file, exit code: $mountExitCode")
            }

            println("DMG mounted, copying content...")

            // Look for the MacOS directory containing the actual binaries
            val vlcApp = File(mountPoint, "VLC.app")
            val macOSDir = File(vlcApp, "Contents/MacOS")

            if (macOSDir.exists() && macOSDir.isDirectory) {
                // Copy all files from MacOS directory directly to targetDir
                println("Copying files from VLC.app/Contents/MacOS directly to target directory")
                macOSDir.listFiles()?.forEach { file ->
                    val targetFile = File(targetDir, file.name)
                    if (file.isDirectory) {
                        copyDirectory(file, targetFile)
                    } else {
                        Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                println("Files copied successfully")
            } else {
                println("VLC.app/Contents/MacOS not found in mounted DMG")
            }

            // Unmount DMG
            val unmountProcess =
                ProcessBuilder(
                    "hdiutil",
                    "detach",
                    mountPoint.absolutePath,
                ).start()

            val unmountExitCode = unmountProcess.waitFor()
            if (unmountExitCode != 0) {
                println("Warning: Failed to unmount DMG, exit code: $unmountExitCode")
            }

            println("Processing complete")
        } catch (e: Exception) {
            println("Error during download or DMG processing: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun copyDirectory(
        source: File,
        target: File,
    ) {
        if (!target.exists()) {
            target.mkdirs()
        }

        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                copyDirectory(file, targetFile)
            } else {
                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

// Register task
tasks.register<DownloadVlcTask>("downloadVlc")
