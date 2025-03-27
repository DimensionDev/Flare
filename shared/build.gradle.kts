import java.util.Locale

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.skie)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.room)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    androidTarget()

    jvm()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
//        macosArm64(),
//        macosX64(),
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "shared"
//            isStatic = true
            linkerOpts("-ld_classic")
        }
    }
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
                implementation(libs.bundles.kotlinx)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                api(libs.paging.common)
                implementation(libs.bundles.ktorfit)
                implementation(libs.bundles.ktor)
                implementation(libs.okio)
                implementation(libs.kermit)
                implementation(libs.kotlin.codepoints.deluxe)
                implementation(libs.ksoup)
                implementation(libs.mfm.multiplatform)
                implementation(libs.twitter.parser)
                implementation(libs.molecule.runtime)
                api(libs.bluesky)
                implementation(libs.room.runtime)
                implementation(libs.room.paging)
                implementation(libs.sqlite.bundled)
                implementation(libs.datastore)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.xmlUtil)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidJvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.foundation)
                implementation(libs.ktor.client.okhttp)
            }
        }
        val androidMain by getting {
            dependsOn(androidJvmMain)
            dependencies {
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.core.ktx)
                implementation(libs.koin.android)
            }
        }
        val jvmMain by getting {
            dependsOn(androidJvmMain)
            dependencies {
                implementation(libs.commons.lang3)
                // TODO: workaround for https://issuetracker.google.com/issues/396148592
                implementation("androidx.sqlite:sqlite-jvm:2.5.0-rc03")
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation(libs.stately.isolate)
                implementation(libs.stately.iso.collections)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "dev.dimension.flare.shared"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
    kotlin {
        jvmToolchain(libs.versions.java.get().toInt())
    }
    buildFeatures {
        buildConfig = true
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { element -> element.file.path.contains("build", ignoreCase = true) }
        exclude { element -> element.file.absolutePath.contains("data/network/misskey/api/", ignoreCase = true) }
        exclude { element -> element.file.absolutePath.contains("data/network/xqt/", ignoreCase = true) }
    }
}

skie {
    analytics {
        disableUpload.set(true)
        enabled.set(false)
    }
    features {
        enableSwiftUIObservingPreview = true
    }
}

afterEvaluate {
//    val kspCommonMainKotlinMetadata by tasks
    val runKtlintFormatOverCommonMainSourceSet by tasks
    val runKtlintCheckOverCommonMainSourceSet by tasks
    runKtlintFormatOverCommonMainSourceSet.dependsOn("kspCommonMainKotlinMetadata")
    runKtlintCheckOverCommonMainSourceSet.dependsOn("kspCommonMainKotlinMetadata")
}
