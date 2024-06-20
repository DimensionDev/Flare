
import app.cash.sqldelight.core.capitalize

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.skie)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
//        macosArm64(),
//        macosX64(),
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
            embedBitcode(org.jetbrains.kotlin.gradle.plugin.mpp.BitcodeEmbeddingMode.DISABLE)
        }
    }
    targets.forEach { target ->
        target.name.takeIf {
            it != "metadata"
        }?.let {
            "ksp${it.capitalize()}"
        }?.let {
            dependencies.add(it, libs.ktorfit.ksp)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.sqldelight)
                implementation(libs.bundles.kotlinx)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                api(libs.paging.common)
                implementation(libs.bundles.ktorfit)
                implementation(libs.bundles.ktor)
                implementation(libs.okio)
                implementation(libs.uuid)
                implementation(libs.napier)
                implementation(libs.kotlin.codepoints.deluxe)
                implementation(libs.ktml)
                implementation(libs.mfm.multiplatform)
                implementation(libs.twitter.parser)
                implementation(libs.molecule.runtime)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.sqldelight.android.driver)
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
                api(libs.bluesky)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation(libs.sqldelight.native.driver)
                implementation(libs.stately.isolate)
                implementation(libs.stately.iso.collections)
                api(libs.bluesky.get().toString()) {
                    exclude("co.touchlab.skie")
                }
                implementation("co.touchlab.skie:runtime-kotlin:${libs.versions.skie.get()}")
            }
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("dev.dimension.flare.data.database.app")
            srcDirs("src/commonMain/sqldelight/app")
        }
        create("CacheDatabase") {
            packageName.set("dev.dimension.flare.data.database.cache")
            srcDirs("src/commonMain/sqldelight/cache")
        }
        create("VersionDatabase") {
            packageName.set("dev.dimension.flare.data.database.version")
            srcDirs("src/commonMain/sqldelight/version")
        }
    }
    linkSqlite.set(true)
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