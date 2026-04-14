import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.shared"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
        )
        ksp(
            libs.ktorfit.ksp,
            libs.room.compiler,
        )
    }
    android {
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.uuid.ExperimentalUuidApi")
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.bundles.kotlinx)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                api(libs.paging.common)
                api(libs.paging.compose)
                implementation(libs.bundles.ktorfit)
                implementation(libs.bundles.ktor)
                implementation(libs.okio)
                implementation(libs.kotlin.codepoints.deluxe)
                implementation(libs.ksoup)
                implementation(libs.mfm.multiplatform)
                implementation(libs.twitter.parser)
                implementation(libs.molecule.runtime)
                api(libs.bluesky)
                api(libs.bluesky.oauth)
                implementation(libs.room.runtime)
                implementation(libs.room.paging)
                implementation(libs.sqlite.bundled)
                implementation(libs.datastore)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.xmlUtil)
                implementation(libs.ktor.client.resources)
                implementation(libs.cryptography.provider.optimal)
                implementation(libs.openai.client)
                implementation(libs.nostr.sdk.kmp)
                implementation(libs.readability)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.paging.testing)
                implementation(libs.ktor.client.mock)
            }
        }
        val androidJvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.core.ktx)
                implementation(libs.koin.android)
                implementation(libs.koin.compose)
                implementation(libs.activity.compose)
            }
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.robolectric)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.commons.lang3)
                implementation(libs.prettytime)
                implementation(libs.jna)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

ktorfit {
    compilerPluginVersion.set("2.3.3")
}

afterEvaluate {
//    val kspCommonMainKotlinMetadata by tasks
    val runKtlintFormatOverCommonMainSourceSet by tasks
    val runKtlintCheckOverCommonMainSourceSet by tasks
    runKtlintFormatOverCommonMainSourceSet.dependsOn("kspCommonMainKotlinMetadata")
    runKtlintCheckOverCommonMainSourceSet.dependsOn("kspCommonMainKotlinMetadata")
    tasks {
        configureEach {
            if (this.name != "kspCommonMainKotlinMetadata" && this.name.startsWith("ksp")) {
                this.dependsOn("kspCommonMainKotlinMetadata")
            }
        }
    }
}
