import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
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
            allWarningsAsErrors.set(true)
            freeCompilerArgs.set(listOf(
                "-Xexpect-actual-classes",
                "-Xconsistent-data-class-copy-visibility",
            ))
        }
    }
    afterEvaluate {
        // TODO: workaround for https://youtrack.jetbrains.com/issue/KT-72068
        if (tasks.findByName("embedAndSignAppleFrameworkForXcode") != null) {
            val embedAndSignAppleFrameworkForXcode by tasks
            embedAndSignAppleFrameworkForXcode.dependsOn(":commonizeNativeDistribution")
        }
    }
}
