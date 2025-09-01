import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.shared)
    implementation(projects.composeUi)

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.desktop.common)
    implementation(compose.components.resources)
    implementation(libs.precompose.molecule)
    implementation(libs.lifecycle.runtime.compose)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.fluent.ui)
    implementation(libs.composeIcons.fontAwesome)
    implementation(libs.lifecycle.viewmodel.compose)
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
    implementation(libs.platformtools.darkmodedetector)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.jna)
}

compose.desktop {
    application {
        mainClass = "dev.dimension.flare.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Pkg)
            packageName = "Flare"
            packageVersion = "1.0.0"
            macOS {
                val file = project.file("signing.properties")
                val hasSigningProps = file.exists()
                packageBuildVersion = System.getenv("BUILD_NUMBER") ?: "21"
                bundleID = "dev.dimension.flare"
                minimumSystemVersion = "12.0"
                appStore = hasSigningProps

                jvmArgs(
                    "-Dapple.awt.application.appearance=system",
                )

                infoPlist {
                    extraKeysRawXml = macExtraPlistKeys
                }

                if (hasSigningProps) {
                    val signingProp = Properties()
                    signingProp.load(file.inputStream())
                    signing {
                        sign.set(true)
                        identity.set(signingProp.getProperty("identity"))
                    }

                    entitlementsFile.set(project.file(signingProp.getProperty("entitlementsFile")))
                    runtimeEntitlementsFile.set(project.file(signingProp.getProperty("runtimeEntitlementsFile")))
                    provisioningProfile.set(project.file(signingProp.getProperty("provisioningProfile")))
                    runtimeProvisioningProfile.set(project.file(signingProp.getProperty("runtimeProvisioningProfile")))
                }

                iconFile.set(project.file("resources/ic_launcher.icns"))
            }
            linux {
                modules("jdk.security.auth")
            }
            appResourcesRootDir.set(file("resources"))
        }
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
extra["composeMediaPlayerVersion"] = libs.versions.composemediaplayer.get()
extra["jnaVersion"] = libs.versions.jna.get()
extra["nativeDestDir"] = "resources/macos-arm64"

apply(from = File(projectDir, "install-native-libs.gradle.kts"))
apply(from = File(projectDir, "build-swift.gradle.kts"))
