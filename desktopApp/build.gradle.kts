
import org.jetbrains.compose.compose

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.stability.analyzer)
    id("io.github.kdroidfilter.nucleus") version "1.1.6"
}

dependencies {
    implementation(projects.shared)
    implementation(projects.composeUi)

    implementation(compose("org.jetbrains.compose.runtime:runtime"))
    implementation(compose("org.jetbrains.compose.foundation:foundation"))
    implementation(compose("org.jetbrains.compose.ui:ui"))
    implementation(compose("org.jetbrains.compose.desktop:desktop"))
    implementation(compose("org.jetbrains.compose.components:components-resources"))
    implementation(libs.precompose.molecule)
    implementation(libs.lifecycle.runtime.compose)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.fluent.ui)
    implementation(libs.composeIcons.fontAwesome)
    implementation(libs.navigation3.desktop)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.adaptive.navigation3)
    implementation(libs.bundles.coil3)
    implementation(libs.bundles.kotlinx)
    implementation(libs.ksoup)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.commons.lang3)
    implementation(libs.zoomable)
    implementation(libs.datastore)
    implementation(libs.reorderable)
    implementation(libs.jna)
    implementation("io.github.kdroidfilter:composemediaplayer:0.8.7")
    implementation("io.github.kdroidfilter:nucleus.darkmode-detector:1.1.6")
    implementation("io.github.kdroidfilter:nucleus.aot-runtime:1.1.6")
    implementation("io.github.kdroidfilter:nucleus.decorated-window:1.1.6")
    implementation("io.github.kdroidfilter:composewebview:1.0.0-alpha-10")
}

nucleus.application {
    jvmArgs += "--enable-native-access=ALL-UNNAMED"
    mainClass = "dev.dimension.flare.MainKt"
    nativeDistributions {
        cleanupNativeLibs = true
        enableAotCache = true
        homepage = "https://github.com/DimensionDev/Flare"
        appResourcesRootDir.set(file("resources"))
        targetFormats(
            io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat.Pkg,
            io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat.Flatpak,
            io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat.AppX,
        )
        packageName = "Flare"
        val buildVersion = System.getenv("BUILD_VERSION")?.toString()?.takeIf {
            // match semantic versioning
            Regex("""\d+\.\d+\.\d+(-\S+)?""").matches(it)
        } ?: "1.0.0"
        packageVersion = buildVersion

        protocol("Flare", "flare")

        macOS {
            val hasSigningProps = project.file("embedded.provisionprofile").exists() && project.file("runtime.provisionprofile").exists()
            packageBuildVersion = System.getenv("BUILD_NUMBER") ?: "1"
            bundleID = "dev.dimension.flare"
            minimumSystemVersion = "14.0"
            appStore = hasSigningProps

            jvmArgs(
                "-Dapple.awt.application.appearance=system",
            )

            infoPlist {
                extraKeysRawXml = macExtraPlistKeys
            }

            if (hasSigningProps) {
                signing {
                    sign.set(true)
                    identity.set("SUJITEKU LIMITED LIABILITY CO.")
                }

                entitlementsFile.set(project.file("entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))
                provisioningProfile.set(project.file("embedded.provisionprofile"))
                runtimeProvisioningProfile.set(project.file("runtime.provisionprofile"))
            }

            iconFile.set(project.file("resources/ic_launcher.icns"))
        }
        windows {
            iconFile.set(project.file("resources/ic_launcher.ico"))
            appx {
            }
        }
        linux {
            iconFile.set(project.file("resources/ic_launcher.png"))
            flatpak {
                runtime = "org.freedesktop.Platform"
                runtimeVersion = "24.08"
                sdk = "org.freedesktop.Sdk"
                branch = "master"
                finishArgs = listOf("--share=ipc", "--socket=x11", "--socket=wayland")
            }
        }
    }
}

//compose.desktop {
//    application {
//        mainClass = "dev.dimension.flare.MainKt"
//
//        nativeDistributions {
//            targetFormats(TargetFormat.Pkg, TargetFormat.Exe, TargetFormat.Deb)
//            packageName = "Flare"
//            val buildVersion = System.getenv("BUILD_VERSION")?.toString()?.takeIf {
//                // match semantic versioning
//                Regex("""\d+\.\d+\.\d+(-\S+)?""").matches(it)
//            } ?: "1.0.0"
//            packageVersion = buildVersion
//            macOS {
//                val hasSigningProps = project.file("embedded.provisionprofile").exists() && project.file("runtime.provisionprofile").exists()
//                packageBuildVersion = System.getenv("BUILD_NUMBER") ?: "1"
//                bundleID = "dev.dimension.flare"
//                minimumSystemVersion = "14.0"
//                appStore = hasSigningProps
//
//                jvmArgs(
//                    "-Dapple.awt.application.appearance=system",
//                )
//
//                infoPlist {
//                    extraKeysRawXml = macExtraPlistKeys
//                }
//
//                if (hasSigningProps) {
//                    signing {
//                        sign.set(true)
//                        identity.set("SUJITEKU LIMITED LIABILITY CO.")
//                    }
//
//                    entitlementsFile.set(project.file("entitlements.plist"))
//                    runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))
//                    provisioningProfile.set(project.file("embedded.provisionprofile"))
//                    runtimeProvisioningProfile.set(project.file("runtime.provisionprofile"))
//                }
//
//                iconFile.set(project.file("resources/ic_launcher.icns"))
//            }
//            windows {
//                iconFile.set(project.file("resources/ic_launcher.ico"))
//            }
//            linux {
//                iconFile.set(project.file("resources/ic_launcher.png"))
//            }
//            appResourcesRootDir.set(file("resources"))
//        }
//        buildTypes {
//            release {
//                proguard {
//                   this.isEnabled.set(false)
//                    // version.set("7.7.0")
//                    // this.configurationFiles.from(
//                        // file("proguard-rules.pro")
//                    // )
//                }
//            }
//        }
//    }
//}

compose.resources {
    packageOfResClass = "dev.dimension.flare"
}


ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { element -> element.file.path.contains("build", ignoreCase = true) }
    }
}

// register deeplinks
val macExtraPlistKeys: String
    get() = """
      <key>CFBundleURLTypes</key>
      <array>
        <dict>
          <key>CFBundleURLName</key>
          <string>FlareScheme</string>
          <key>CFBundleURLSchemes</key>
          <array>
            <string>flare</string>
          </array>
        </dict>
      </array>
      <key>ITSAppUsesNonExemptEncryption</key>
      <false/>
      <key>LSMultipleInstancesProhibited</key>
      <true/>
    """


extra["sqliteVersion"] = libs.versions.sqlite.get()
extra["sqliteOsArch"] = "osx_arm64"
extra["jnaVersion"] = libs.versions.jna.get()
extra["nativeDestDir"] = "resources/macos-arm64"

//apply(from = File(projectDir, "install-native-libs.gradle.kts"))
//apply(from = File(projectDir, "build-swift.gradle.kts"))



abstract class GenerateSupportedLocales : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val resRoot = project.file("src/main/composeResources")

        val locales = resRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values-") }
            ?.map { it.name.removePrefix("values-") } // e.g. "ja", "zh", "zh-rCN"
            ?.distinct()
            ?.sorted()
            ?.map { it.replace("-r", "-") }
            ?: emptyList()

        val pkg = "dev.dimension.flare"
        val outDir = outputDir.get().asFile
        val outFile = File(outDir, "SupportedLocales.kt")

        outDir.mkdirs()
        outFile.writeText(
            """
            package $pkg

            object SupportedLocales {
                val tags: List<String> = listOf(
                    "en-US",
                    ${locales.joinToString(",\n                    ") { "\"$it\"" }}
                )
            }
            """.trimIndent()
        )
    }
}

val generateSupportedLocales = tasks.register<GenerateSupportedLocales>("generateSupportedLocales") {
    outputDir.set(layout.buildDirectory.dir("generated/supportedLocales"))
}
kotlin {
    sourceSets {
        val main by getting {
            kotlin.srcDir(generateSupportedLocales.map { it.outputDir })
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateSupportedLocales)
}
