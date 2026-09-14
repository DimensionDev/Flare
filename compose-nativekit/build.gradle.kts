plugins {
    id("dev.dimension.compose.nativekit.root-conventions")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.moko.resources) apply false
}

allprojects {
    group = "dev.dimension.compose.nativekit"
    version = "0.1.0-SNAPSHOT"
}
