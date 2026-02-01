import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import org.jetbrains.compose.compose
import java.util.Locale

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.room)
    alias(libs.plugins.stability.analyzer)
}

kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("apple") {
                withIos()
            }
            group("androidJvm") {
                // TODO: https://youtrack.jetbrains.com/issue/KT-80409
                withCompilations { it is KotlinMultiplatformAndroidCompilation }
                withJvm()
            }
        }
    }
    jvmToolchain(libs.versions.java.get().toInt())
    explicitApi()
    androidLibrary {
        compileSdk = libs.versions.compileSdk.get().toInt()
        namespace = "dev.dimension.flare.shared"
        minSdk = libs.versions.minSdk.get().toInt()
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    targets.forEach { target ->
        target.name.takeIf {
            it != "metadata"
        }?.let {
            "ksp${it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}"
        }?.let {
            dependencies.add(it, libs.ktorfit.ksp)
            dependencies.add(it, libs.room.compiler)
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
                implementation(compose("org.jetbrains.compose.runtime:runtime"))
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
                implementation(projects.shared.api)
                implementation(libs.ktor.client.resources)
                implementation(libs.cryptography.provider.optimal)
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
                implementation(compose("org.jetbrains.compose.foundation:foundation"))
                implementation(libs.ktor.client.okhttp)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.core.ktx)
                implementation(libs.koin.android)
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
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ktorfit {
    compilerPluginVersion.set("2.3.3")
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { element -> element.file.path.contains("build", ignoreCase = true) }
        exclude { element -> element.file.absolutePath.contains("data/network/misskey/api/", ignoreCase = true) }
        exclude { element -> element.file.absolutePath.contains("data/network/xqt/", ignoreCase = true) }
    }
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
