plugins {
    id("dev.dimension.flareui.root-conventions")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.jvm.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {
    group = "dev.dimension.flareui"
    version = "0.1.0-SNAPSHOT"
}
