import dev.dimension.compose.nativekit.buildlogic.NativeKitPlatform
import dev.dimension.compose.nativekit.buildlogic.nativeKit
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("dev.dimension.compose.nativekit.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    nativeKit {
        namespace = "dev.dimension.compose.nativekit.lazy"
        platforms(
            NativeKitPlatform.ANDROID,
            NativeKitPlatform.JVM,
            NativeKitPlatform.IOS,
            NativeKitPlatform.MACOS,
        )
    }
    android {
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("collectionLayout") {
            includeDirs("src/nativeInterop/cinterop")
        }
        if (providers.gradleProperty("nativeKitLazyBenchmark").orNull == "true") {
            binaries.test("benchmark", listOf(NativeBuildType.RELEASE))
            binaries.framework("benchmark", listOf(NativeBuildType.RELEASE)) {
                baseName = "ComposeNativeKitLazyBenchmark"
                compilation = compilations.getByName("test")
                isStatic = true
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":foundation"))
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime.saveable)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
                implementation(libs.recyclerview)
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.compose.ui.test.manifest)
                implementation(libs.compose.material3)
                implementation(libs.junit)
                implementation(libs.robolectric)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
