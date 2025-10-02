import org.jetbrains.compose.compose

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.skie)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    explicitApi()
    applyDefaultHierarchyTemplate()
    androidLibrary {
        compileSdk = libs.versions.compileSdk.get().toInt()
        namespace = "dev.dimension.flare.compose.ui"
        minSdk = libs.versions.minSdk.get().toInt()
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
        enableCoreLibraryDesugaring = true
    }
    jvm()
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "KotlinSharedUI"
            isStatic = true
            export(projects.shared)
        }
    }

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
                implementation(libs.coil3.ktor3)
                implementation(libs.coil3.network)
                implementation(libs.compose.placeholder.foundation)
                implementation(libs.ksoup)
                implementation(libs.kotlinx.immutable)
                implementation(libs.precompose.molecule)
                implementation(libs.kotlinx.datetime)
                implementation(libs.datastore)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
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
                implementation(libs.material3)
                implementation(libs.bundles.media3)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.bundles.koin)
                implementation(libs.haze)
                implementation(libs.haze.materials)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.fluent.ui)
                implementation(libs.koin.compose)
                implementation(libs.androidx.collection)
                implementation(libs.prettytime)
            }
        }
        val iosMain by getting {
            dependencies {
                api(projects.shared)
                implementation(libs.cupertino)
                api(compose.uiUtil)
                implementation("com.composables:core:1.44.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
            }
        }
    }
}

skie {
    analytics {
        disableUpload.set(true)
        enabled.set(false)
    }
    features {
        enableSwiftUIObservingPreview = true
        enableFlowCombineConvertorPreview = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { element -> element.file.path.contains("build", ignoreCase = true) }
    }
}


compose.resources {
    packageOfResClass = "dev.dimension.flare.compose.ui"
    generateResClass = always
}
