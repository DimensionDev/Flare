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
    implementation(compose.components.uiToolingPreview)
    implementation(libs.precompose.molecule)
    implementation(libs.lifecycle.runtime.compose)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.fluent.ui)
    implementation(libs.window.styler)
    implementation(libs.jSystemThemeDetector)
    implementation(libs.composeIcons.fontAwesome)
    implementation(libs.jetbrains.navigation.compose)
    implementation(libs.bundles.coil3)
    implementation(libs.bundles.kotlinx)
    implementation(libs.ksoup)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.commons.lang3)
}

compose.desktop {
    application {
        mainClass = "dev.dimension.flare.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.dimension.flare"
            packageVersion = "1.0.0"
        }
        buildTypes {
            release {
                proguard {
                    version.set("7.6.1")
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

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { element -> element.file.path.contains("build", ignoreCase = true) }
    }
}