val swiftSource = layout.projectDirectory.file("src/main/swift/WebviewBridge.swift")
val targetLib = layout.projectDirectory.dir("resources/macos-arm64").file("libWebviewBridge.dylib").asFile
val isMac = org.gradle.internal.os.OperatingSystem.current().isMacOsX

tasks.register<Exec>("compileWebviewBridgeArm64") {
    onlyIf { isMac }

    inputs.file(swiftSource)
    outputs.file(targetLib)

    doFirst {
        targetLib.parentFile.mkdirs()
    }
    commandLine(
        "swiftc",
        "-emit-library",
        "-module-name", "WebviewBridge",
        "-framework", "Cocoa",
        "-target", "arm64-apple-macos12",
        "-o", targetLib.absolutePath,
        swiftSource.asFile.absolutePath
    )
}

afterEvaluate {
    tasks.named("compileKotlin").configure {
        dependsOn("compileWebviewBridgeArm64")
    }
    tasks.named("prepareAppResources").configure {
        dependsOn("compileWebviewBridgeArm64")
    }
}