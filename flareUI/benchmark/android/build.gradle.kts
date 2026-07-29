import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.android.jvm.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.dimension.flare.ui.benchmark"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = 0
        }
    }
    defaultConfig {
        minSdk {
            version = release(libs.versions.minSdk.get().toInt())
        }
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
    }
    testBuildType = "release"
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
}

androidComponents {
    onVariants { variant ->
        @Suppress("UnstableApiUsage")
        variant.experimentalProperties.put(
            "android.experimental.force-aot-compilation",
            true,
        )
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    androidTestImplementation(project(":foundation"))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.foundation)
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
