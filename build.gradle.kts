plugins {
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
    alias(libs.plugins.nucleus) apply false
    alias(libs.plugins.koin) apply false
}

//subprojects {
//    val commonOptIn = listOf(
//        "kotlin.time.ExperimentalTime",
//        "kotlin.experimental.ExperimentalObjCRefinement",
//    )
//
//    val freeArgs = listOf(
//        "-Xexpect-actual-classes",
//        "-Xconsistent-data-class-copy-visibility",
//    )
//
//    plugins.withId("org.jetbrains.kotlin.multiplatform") {
//        extensions.configure<KotlinMultiplatformExtension> {
//            compilerOptions {
//                allWarningsAsErrors.set(true)
//                freeCompilerArgs.addAll(freeArgs)
//                optIn.addAll(commonOptIn)
//            }
//            jvmToolchain {
//                languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
//            }
//        }
//    }
//    plugins.withId("com.android.application") {
//        extensions.configure<KotlinAndroidProjectExtension> {
//            compilerOptions {
//                allWarningsAsErrors.set(true)
//                freeCompilerArgs.addAll(freeArgs)
//                optIn.addAll(
//                    commonOptIn + listOf(
//                        "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
//                    )
//                )
//                jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
//            }
//            jvmToolchain {
//                languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
//            }
//        }
//    }
//    plugins.withId("org.jetbrains.kotlin.jvm") {
//        extensions.configure<KotlinJvmExtension> {
//            compilerOptions {
//                allWarningsAsErrors.set(true)
//                freeCompilerArgs.addAll(freeArgs)
//                optIn.addAll(commonOptIn)
//            }
//            jvmToolchain {
//                languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
//            }
//        }
//    }
//
//    plugins.withId("com.android.library") {
//        extensions.configure<LibraryExtension> {
//            compileSdk = libs.versions.compileSdk.get().toInt()
//            defaultConfig {
//                minSdk = libs.versions.minSdk.get().toInt()
//            }
//        }
//    }
//}
