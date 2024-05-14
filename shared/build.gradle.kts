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
    applyDefaultHierarchyTemplate {
        common {
            group("nonAndroid") {
                withApple()
                withJvm()
            }
        }
    }

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

    // export as jar for ikvm
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
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
                implementation(libs.ktorfit.lib)
                implementation(libs.bundles.ktor)
                implementation(libs.okio)
                implementation(libs.uuid)
                implementation(libs.napier)
                implementation(libs.kotlin.codepoints.deluxe)
                implementation(libs.ktml)
                implementation(libs.mfm.multiplatform)
                api(libs.bluesky)
                implementation(libs.twitter.parser)
                implementation(libs.molecule.runtime)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.sqldelight.android.driver)
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:${libs.versions.ktor.get()}")
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation(libs.sqldelight.native.driver)
                implementation(libs.stately.isolate)
                implementation(libs.stately.iso.collections)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.sqldelight.jvm.driver)
                // DO NOT upgrade the version since jvm target should be 1.8, ikvm only supports 1.8
                implementation("org.xerial:sqlite-jdbc:3.39.2.0")
                implementation("io.ktor:ktor-client-okhttp:${libs.versions.ktor.get()}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:${libs.versions.kotlinx.coroutines.get()}")
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
}