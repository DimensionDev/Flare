
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
    implementation(projects.shared.ui.component)

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
    implementation(libs.jSystemThemeDetector)
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
}

compose.desktop {
    application {
        mainClass = "dev.dimension.flare.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.dimension.flare"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "dev.dimension.flare"
                infoPlist {
                    extraKeysRawXml = macExtraPlistKeys
                }
//                iconFile.set(project.file("src/jvmMain/resources/icon/ic_launcher.icns"))
            }
            appResourcesRootDir.set(file("resources"))
        }
        buildTypes {
            release {
                proguard {
                    version.set("7.7.0")
                    this.configurationFiles.from(
                        file("proguard-rules.pro")
                    )
                }
            }
        }
    }
}

compose.resources {
    packageOfResClass = "dev.dimension.flare"
}

tasks.withType<JavaExec> {
    systemProperty("compose.application.resources.dir", file("resources").absolutePath)
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
    """

