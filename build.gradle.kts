import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktorfit) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.composeMultiplatform) apply false
}

subprojects {
    val commonOptIn = listOf(
        "kotlin.time.ExperimentalTime",
    )

    val freeArgs = listOf(
        "-Xexpect-actual-classes",
        "-Xconsistent-data-class-copy-visibility",
        "-Xmulti-dollar-interpolation",
    )

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            compilerOptions {
                allWarningsAsErrors.set(true)
                freeCompilerArgs.addAll(freeArgs)
                optIn.addAll(commonOptIn)
            }
            jvmToolchain(libs.versions.java.get().toInt())
        }
    }
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                allWarningsAsErrors.set(true)
                freeCompilerArgs.addAll(freeArgs)
                optIn.addAll(commonOptIn)
                jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
            }
            jvmToolchain(libs.versions.java.get().toInt())
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk = libs.versions.compileSdk.get().toInt()
            defaultConfig {
                minSdk = libs.versions.minSdk.get().toInt()
            }
        }
    }
}
