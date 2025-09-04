import java.nio.file.Files

val swiftSource = layout.projectDirectory.dir("src/main/swift/macosBridge").asFile
val xcodeproj = layout.projectDirectory.dir("src/main/swift/macosBridge.xcodeproj").asFile
val buildOutput = layout.projectDirectory.file("src/main/swift/build/Release/libmacosBridge.dylib")
val targetLib = layout.projectDirectory.dir("resources/macos-arm64").file("libmacosBridge.dylib").asFile
val isMac = org.gradle.internal.os.OperatingSystem.current().isMacOsX

tasks.register<Exec>("compileMacosBridgeArm64") {
    onlyIf { isMac }

    inputs.files(swiftSource)
    outputs.file(targetLib)

    doFirst {
        targetLib.parentFile.mkdirs()
    }

    commandLine(
        "xcodebuild",
        "-project", xcodeproj.absolutePath,
        "-scheme", "macosBridge",
        "-configuration", "Release",
        "BUILD_DIR=${xcodeproj.parentFile}/build",
    )

    doLast {
        Files.copy(
            buildOutput.asFile.toPath(),
            targetLib.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

afterEvaluate {
    tasks.named("compileKotlin").configure {
        dependsOn("compileMacosBridgeArm64")
    }
    tasks.named("prepareAppResources").configure {
        dependsOn("compileMacosBridgeArm64")
    }
}