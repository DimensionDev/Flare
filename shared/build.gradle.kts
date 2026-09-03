
import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.koin.compiler)
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
            FlarePlatform.WEB,
            FlarePlatform.MACOS,
        )
        ksp(
            libs.ktorfit.ksp,
            libs.room.compiler,
        )
    }
    android {
        withHostTest {}
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
                implementation(libs.koin.annotations)
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
                implementation(libs.room.runtime)
                implementation(libs.room.paging)
                implementation(libs.sqlite)
                implementation(libs.sqlite.async)
                implementation(libs.datastore.core)
                implementation(libs.datastore.core.okio)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.ktor.client.resources)
                implementation(libs.cryptography.provider.optimal)
                implementation(libs.openai.client)
            }
        }
        val nonWebMain by getting {
            dependencies {
                implementation(libs.sqlite.bundled)
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
        val androidHostTest by getting {
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
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
                implementation(libs.sqlite.web)
                implementation(libs.kotlinx.browser)
                implementation(npm("@androidx/sqlite-web-worker", file("sqlite-web-worker")))
            }
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

val runDatabaseBenchmark =
    providers
        .gradleProperty("runDatabaseBenchmark")
        .map(String::toBoolean)
        .orElse(false)

tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().configureEach {
    if (runDatabaseBenchmark.get()) {
        outputs.upToDateWhen { false }
        testLogging.showStandardStreams = true
    } else {
        filter.excludeTestsMatching("*TimelineDatabaseBenchmarkTest*")
    }
}

val sqliteBundledJvmNative = configurations.create("sqliteBundledJvmNative")
dependencies.add(
    sqliteBundledJvmNative.name,
    "androidx.sqlite:sqlite-bundled-jvm:${libs.versions.sqlite.get()}",
)

val sqliteNativeLibrary =
    when {
        System.getProperty("os.name").startsWith("Mac") && System.getProperty("os.arch") in setOf("aarch64", "arm64") ->
            "natives/osx_arm64" to "libsqliteJni.dylib"
        System.getProperty("os.name").startsWith("Linux") && System.getProperty("os.arch") in setOf("aarch64", "arm64") ->
            "natives/linux_arm64" to "libsqliteJni.so"
        System.getProperty("os.name").startsWith("Linux") ->
            "natives/linux_x64" to "libsqliteJni.so"
        System.getProperty("os.name").startsWith("Windows") ->
            "natives/windows_x64" to "sqliteJni.dll"
        else -> null
    }

if (sqliteNativeLibrary != null) {
    val extractSqliteBundledJvmNative =
        tasks.register<org.gradle.api.tasks.Sync>("extractSqliteBundledJvmNative") {
            from({ sqliteBundledJvmNative.files.map(::zipTree) }) {
                include("${sqliteNativeLibrary.first}/${sqliteNativeLibrary.second}")
            }
            into(layout.buildDirectory.dir("sqlite-bundled-jvm-native"))
        }

    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        if (name == "testAndroidHostTest") {
            dependsOn(extractSqliteBundledJvmNative)
            systemProperty(
                "androidx.sqlite.driver.bundled.path",
                layout.buildDirectory
                    .dir("sqlite-bundled-jvm-native/${sqliteNativeLibrary.first}")
                    .get()
                    .asFile.absolutePath,
            )
            systemProperty("androidx.sqlite.driver.bundled.name", sqliteNativeLibrary.second)
        }
    }
}
