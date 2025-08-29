import java.nio.file.Files

val swiftSource = layout.projectDirectory.dir("src/main/swift/macosBridge.xcodeproj").asFile
val buildOutput = layout.projectDirectory.file("src/main/swift/build/Release/libmacosBridge.dylib")
val targetLib = layout.projectDirectory.dir("resources/macos-arm64").file("libmacosBridge.dylib").asFile
val isMac = org.gradle.internal.os.OperatingSystem.current().isMacOsX

tasks.register<Exec>("compileWebviewBridgeArm64") {
    onlyIf { isMac }

//    inputs.file(swiftSource)
    outputs.file(targetLib)

    doFirst {
        targetLib.parentFile.mkdirs()
    }

    commandLine(
        "xcodebuild",
        "-project", swiftSource.absolutePath,
        "-target", "macosBridge",
        "-configuration", "Release",
    )

    // copy buildOutput to targetLib
    if (isMac) {
        Files.copy(
            buildOutput.asFile.toPath(),
            targetLib.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

afterEvaluate {
    tasks.named("compileKotlin").configure {
        dependsOn("compileWebviewBridgeArm64")
    }
    tasks.named("prepareAppResources").configure {
        dependsOn("compileWebviewBridgeArm64")
    }
}