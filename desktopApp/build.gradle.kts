
import io.github.kdroidfilter.nucleus.desktop.application.dsl.AppImageCategory
import io.github.kdroidfilter.nucleus.desktop.application.dsl.CompressionLevel
import java.util.Properties
import org.jetbrains.compose.compose

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.stability.analyzer)
    alias(libs.plugins.nucleus)
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
    implementation(libs.nucleus.darkmode.detector)
    implementation(libs.nucleus.aot.runtime)
    implementation(libs.nucleus.decorated.window)
    implementation(libs.composewebview)
    implementation("io.github.kdroidfilter:composemediaplayer:${libs.versions.composemediaplayer.get()}") {
        // https://github.com/kdroidFilter/ComposeMediaPlayer/blob/13cb1d94382f300d338c6ca3b9098c52b2b61d6a/mediaplayer/build.gradle.kts#L82
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-test")
    }
    implementation(libs.systemColor)
}

nucleus.application {
    jvmArgs += "--enable-native-access=ALL-UNNAMED"
    mainClass = "dev.dimension.flare.MainKt"
    buildTypes {
        release {
            proguard {
                this.isEnabled.set(false)
                // version.set("7.7.0")
                // this.configurationFiles.from(
                // file("proguard-rules.pro")
                // )
            }
        }
    }
    nativeDistributions {
        cleanupNativeLibs = true
        enableAotCache = true
        modules("jdk.localedata")
        homepage = "https://github.com/DimensionDev/Flare"
        // Higher compression level can cause laggy for linux AppImage
        compressionLevel = CompressionLevel.Store
        targetFormats(
            io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat.Pkg,
            io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat.AppImage,
            io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat.AppX,
        )
        packageName = "Flare"
        val fdroidProp = Properties()
        val fdroid = rootProject.file("fdroid.properties")
        fdroidProp.load(fdroid.inputStream())
        val buildVersion = System.getenv("BUILD_VERSION")?.takeIf {
            // match semantic versioning
            Regex("""\d+\.\d+\.\d+(-\S+)?""").matches(it)
        } ?: fdroidProp.getProperty("versionName") ?: "1.0.0"
        packageVersion = buildVersion
        artifactName = $$"Flare-$${buildVersion}.${ext}"

        protocol("Flare", "flare")

        macOS {
            val hasSigningProps = project.file("embedded.provisionprofile").exists() && project.file("runtime.provisionprofile").exists()
            packageBuildVersion = System.getenv("BUILD_NUMBER") ?: fdroidProp.getProperty("versionCode")
                ?.toIntOrNull()?.toString() ?: "1"
            bundleID = "dev.dimension.flare"
            minimumSystemVersion = "14.0"
            appStore = hasSigningProps
            appCategory = "public.app-category.social-networking"

            jvmArgs(
                "-Dapple.awt.application.appearance=system",
            )

            infoPlist {
                extraKeysRawXml = """
      <key>ITSAppUsesNonExemptEncryption</key>
      <false/>
      <key>LSMultipleInstancesProhibited</key>
      <true/>
    """
            }

            if (hasSigningProps) {
                signing {
                    sign.set(true)
                    identity.set("SUJITEKU LIMITED LIABILITY CO. (7LFDZ96332)")
                }

                entitlementsFile.set(project.file("entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))
                provisioningProfile.set(project.file("embedded.provisionprofile"))
                runtimeProvisioningProfile.set(project.file("runtime.provisionprofile"))
            }

            iconFile.set(project.file("resources/ic_launcher.icns"))
            layeredIconDir.set(rootProject.file("iosApp/flare/AppIcon.icon"))
        }
        windows {
            iconFile.set(project.file("resources/ic_launcher.ico"))
            appx {
                applicationId = "FlareApp"
                publisherDisplayName = "Tlaster"
                displayName = "FlareApp"
                publisher = "CN=F82B0EE7-EF8D-4515-BEAB-DD968D07D67F"
                identityName = "51945Tlaster.FlareApp"

                val resRoot = project.file("src/main/composeResources")

                val locales = resRoot.listFiles()
                    ?.filter { it.isDirectory && it.name.startsWith("values-") }
                    ?.map { it.name.removePrefix("values-") } // e.g. "ja", "zh", "zh-rCN"
                    ?.distinct()
                    ?.sorted()
                    ?.map { it.replace("-r", "-") }
                    ?: emptyList()
                languages = listOf("en-US") + locales - "sr-SP" // Windows doesn't support sr-SP
                backgroundColor = "#09AD9F"
                showNameOnTiles = true
                minVersion = "10.0.17763.0"
                capabilities = listOf("runFullTrust")

                storeLogo.set(project.file("resources/appx/StoreLogo.scale-100.png"))
                square44x44Logo.set(project.file("resources/appx/Square44x44Logo.scale-100.png"))
                square150x150Logo.set(project.file("resources/appx/Square150x150Logo.scale-100.png"))
                wide310x150Logo.set(project.file("resources/appx/Wide310x150Logo.scale-100.png"))
            }
        }
        linux {
            iconFile.set(project.file("resources/ic_launcher.png"))
            appCategory = "Network"
            appImage {
                category = AppImageCategory.Network
                genericName = "Flare"
            }
        }
    }
}

compose.resources {
    packageOfResClass = "dev.dimension.flare"
}


ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { element -> element.file.path.contains("build", ignoreCase = true) }
    }
}

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
