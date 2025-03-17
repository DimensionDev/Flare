import org.jetbrains.compose.compose

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    androidTarget()
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.shared)
                implementation(compose.ui)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.uiUtil)
                implementation(compose("org.jetbrains.compose.ui:ui-graphics"))
                implementation(compose.components.resources)
                implementation(libs.composeIcons.fontAwesome)
                implementation(libs.coil3.compose)
                implementation(libs.compose.placeholder.foundation)
                implementation(libs.ksoup)
                implementation(libs.kotlinx.immutable)
                implementation(libs.precompose.molecule)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.compose.placeholder.material3)
                implementation(libs.material3.adaptive)
                implementation(compose.material3)
                implementation(libs.bundles.media3)
                implementation(libs.bundles.koin)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.fluent.ui)
                implementation(compose("org.jetbrains.compose.material3:material3-window-size-class"))
                api(libs.bundles.mediamp)
            }
        }
    }
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "dev.dimension.flare.shared.ui.component"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
    kotlin {
        jvmToolchain(libs.versions.java.get().toInt())
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { element -> element.file.path.contains("build", ignoreCase = true) }
    }
}


compose.resources {
    packageOfResClass = "dev.dimension.flare.ui.component"
}
