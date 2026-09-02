
import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare
import org.jetbrains.compose.compose

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.compose.ui"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
        )
    }
    android {
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.feature.login)
                implementation(projects.shared)
                implementation(projects.social.bluesky)
                implementation(projects.social.mastodon)
                implementation(projects.social.misskey)
                implementation(projects.social.nostr)
                implementation(projects.social.vvo)
                implementation(projects.social.xqt)
                implementation(projects.feature.subscription)
                implementation(compose("org.jetbrains.compose.ui:ui"))
                implementation(compose("org.jetbrains.compose.runtime:runtime"))
                implementation(compose("org.jetbrains.compose.foundation:foundation"))
                implementation(compose("org.jetbrains.compose.ui:ui-util"))
                implementation(compose("org.jetbrains.compose.ui:ui-graphics"))
                implementation(compose("org.jetbrains.compose.components:components-resources"))
                implementation(libs.composeIcons.fontAwesome)
                implementation(libs.coil3.compose)
                implementation(libs.coil3.ktor3)
                implementation(libs.coil3.network)
                implementation(libs.compose.placeholder)
                implementation(libs.ksoup)
                implementation(libs.kotlinx.immutable)
                implementation(libs.precompose.molecule)
                implementation(libs.kotlinx.datetime)
                implementation(libs.datastore)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.annotations)
                implementation(libs.qrose)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            languageSettings {
                optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            }
            dependencies {
                implementation(libs.material3.adaptive)
                implementation(libs.material3)
                implementation(libs.bundles.media3)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.bundles.koin)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.fluent.ui)
                implementation(libs.koin.compose)
                implementation(libs.androidx.collection)
                implementation("io.github.kdroidfilter:composemediaplayer:${libs.versions.composemediaplayer.get()}") {
                    // https://github.com/kdroidFilter/ComposeMediaPlayer/blob/13cb1d94382f300d338c6ca3b9098c52b2b61d6a/mediaplayer/build.gradle.kts#L82
                    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-test")
                }
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

compose.resources {
    packageOfResClass = "dev.dimension.flare.compose.ui"
    generateResClass = always
}
