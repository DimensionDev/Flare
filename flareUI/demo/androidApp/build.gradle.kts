import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.android-application")
    alias(libs.plugins.compose.compiler)
}

flare {
    namespace = "dev.dimension.flare.flareui.demo.android"
    applicationId = "dev.dimension.flare.flareui.demo"
}

android {
    defaultConfig {
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(projects.flareUI.demo.shared)
    implementation(projects.flareUI.androidCompose)
    implementation(projects.flareUI.androidView)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.material.components)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
