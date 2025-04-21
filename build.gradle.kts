import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
            freeCompilerArgs.set(
                listOf(
                    "-Xexpect-actual-classes",
                    "-Xconsistent-data-class-copy-visibility",
                    "-Xmulti-dollar-interpolation",
                )
            )
        }
    }
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "net.java.dev.jna" && requested.name == "jna") {
                useTarget("net.java.dev.jna:jna:5.17.0")
            }
        }
    }
}
