import dev.dimension.flareui.buildlogic.flareUiApplication

plugins {
    id("dev.dimension.flareui.android-application")
    alias(libs.plugins.compose.compiler)
}

flareUiApplication {
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
    implementation(project(":demo:shared"))
    implementation(project(":android-compose"))
    implementation(project(":android-view"))
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.material.components)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
