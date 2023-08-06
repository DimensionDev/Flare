// Top-level build file where you can add configuration options common to all sub-projects/modules.
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktorfit) apply false
    alias(libs.plugins.ktlint) apply false
}
true // Needed to make the Suppress annotation work for the plugins block

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
//            allWarningsAsErrors = true
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xcontext-receivers"
            )
        }
    }
}