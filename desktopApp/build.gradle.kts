import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

dependencies {
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose("org.jetbrains.compose.material3:material3-window-size-class"))
    implementation(compose.desktop.common)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)
    implementation(libs.precompose.molecule)
    implementation(libs.lifecycle.runtime.compose)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(projects.shared)
    implementation(libs.fluent.ui)
    implementation(libs.window.styler)
    implementation(libs.jSystemThemeDetector)
    implementation(libs.composeIcons.fontAwesome)
}

compose.desktop {
    application {
        mainClass = "dev.dimension.flare.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.dimension.flare"
            packageVersion = System.getenv("BUILD_VERSION")?.toString() ?: "1.0.0"
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