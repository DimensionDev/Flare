// START Non-FOSS component
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin
// END Non-FOSS component
import com.google.gms.googleservices.GoogleServicesPlugin
import dev.dimension.flare.buildlogic.flare
import java.io.File
import java.util.Properties

plugins {
    id("dev.dimension.flare.android-application")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.google.services) apply false
    // START Non-FOSS component
    alias(libs.plugins.firebase.crashlytics) apply false
    // END Non-FOSS component
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}

val rustAndroidAbis = providers.gradleProperty("rustAndroidAbis")
    .orElse("arm64-v8a,armeabi-v7a,x86,x86_64")
val rustRoot = rootProject.layout.projectDirectory.dir("rust")
val rustCargo = providers.environmentVariable("CARGO")
    .orElse("${System.getProperty("user.home")}/.cargo/bin/cargo")
val rustJust = providers.environmentVariable("JUST").orElse("just")
val rustSources = fileTree(rustRoot) {
    include("Cargo.toml", "Cargo.lock", ".cargo/*.toml", "crates/**/*.toml", "crates/**/*.rs")
    exclude("**/target/**")
}
val rustJniLibs = rustRoot.dir("target/android/jniLibs")
val boltffiExecutable = rustRoot.file("target/tools/bin/" + if (System.getProperty("os.name").startsWith("Windows")) "boltffi.exe" else "boltffi")
extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    filter {
        exclude { element -> element.file.invariantSeparatorsPath.contains("/rust/bindings/") }
    }
}
val generateCruxTypes = tasks.register<Exec>("generateCruxTypes") {
    inputs.files(rustSources, rustRoot.file("Justfile"))
    outputs.dir(rustRoot.dir("bindings/types/kotlin"))
    outputs.file(boltffiExecutable)
    workingDir(rustRoot)
    environment("CARGO", rustCargo.get())
    commandLine(rustJust.get(), "--justfile", rustRoot.file("Justfile").asFile.absolutePath, "android-types")
}
val rustNdkDirectory = androidComponents.sdkComponents.ndkDirectory
val rustAndroidOverlay = layout.buildDirectory.file("generated/rust/android.toml")
val buildRustNative = tasks.register<Exec>("buildRustNative") {
    dependsOn(generateCruxTypes)
    inputs.files(rustSources, rustNdkDirectory.map { it.file("source.properties") })
    inputs.property("abis", rustAndroidAbis)
    outputs.dir(rustJniLibs)
    outputs.dir(rustRoot.dir("bindings/kotlin"))
    workingDir(rustRoot.dir("crates/flare-ffi"))
    doFirst {
        val architectures = rustAndroidAbis.get().split(",").map { abi ->
            when (abi) {
                "arm64-v8a" -> "arm64"
                "armeabi-v7a" -> "armv7"
                "x86" -> "x86"
                "x86_64" -> "x86_64"
                else -> error("Unsupported Rust Android ABI: $abi")
            }
        }
        val overlay = rustAndroidOverlay.get().asFile
        overlay.parentFile.mkdirs()
        overlay.writeText("[targets.android]\narchitectures = [" + architectures.joinToString(",") { "\"$it\"" } + "]\n")
        environment("ANDROID_NDK_HOME", rustNdkDirectory.get().asFile.absolutePath)
        environment("PATH", file(rustCargo.get()).parent + File.pathSeparator + System.getenv("PATH").orEmpty())
        commandLine(
            boltffiExecutable.asFile.absolutePath,
            "--cargo-arg=--locked", "--overlay", overlay.absolutePath,
            "pack", "android", "--release",
        )
    }
}
tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
    mustRunAfter(generateCruxTypes, buildRustNative)
}

flare {
    namespace = "dev.dimension.flare"
    applicationId = "dev.dimension.flare"
}

// START Non-FOSS component
if (project.file("google-services.json").exists()) {
    apply<GoogleServicesPlugin>()
    apply<CrashlyticsPlugin>()
}
// END Non-FOSS component

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

android {
    ndkVersion = "28.2.13676358"
    val fdroid = rootProject.file("fdroid.properties")
    val fdroidProp = Properties()
    fdroidProp.load(fdroid.inputStream())

    defaultConfig {
        ndk { abiFilters.addAll(rustAndroidAbis.get().split(",")) }
        versionCode =
            System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: fdroidProp.getProperty("versionCode")
                ?.toIntOrNull() ?: 1
        versionName =
            System.getenv("BUILD_VERSION") ?: fdroidProp.getProperty("versionName") ?: "0.0.0"
    }

    sourceSets.getByName("main") {
        kotlin.directories.add(rootProject.file("rust/bindings/kotlin").absolutePath)
        kotlin.directories.add(rootProject.file("rust/bindings/types/kotlin/dev").absolutePath)
        kotlin.directories.add(rootProject.file("rust/bindings/types/kotlin/com").absolutePath)
        jniLibs.srcDir(rustJniLibs.asFile)
    }

    val file = rootProject.file("signing.properties")
    val hasSigningProps = file.exists()

    signingConfigs {
        if (hasSigningProps) {
            create("flare") {
                val signingProp = Properties()
                signingProp.load(file.inputStream())
                storeFile = rootProject.file(signingProp.getProperty("storeFile"))
                storePassword = signingProp.getProperty("storePassword")
                keyAlias = signingProp.getProperty("keyAlias")
                keyPassword = signingProp.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasSigningProps) {
                signingConfig = signingConfigs.getByName("flare")
            }
        }
        release {
            optimization {
                enable = true
                keepRules {
                    files.add(project.file("rust-proguard-rules.pro"))
                }
            }
            // START Non-FOSS component
            if (project.file("google-services.json").exists()) {
                configure<CrashlyticsExtension> {
                    // Crashlytics 3.0.8 still detects the legacy minify flag.
                    mappingFileUploadEnabled = true
                }
            }
            // END Non-FOSS component
            if (hasSigningProps) {
                signingConfig = signingConfigs.getByName("flare")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    // START Non-FOSS component
    if (project.file("google-services.json").exists()) {
        sourceSets.getByName("main").kotlin.directories.add("src/play/java")
    }
    // END Non-FOSS component
    if (!project.file("google-services.json").exists()){
        sourceSets.getByName("main").kotlin.directories.add("src/foss/java")
    }
}

tasks.named("preBuild") { dependsOn(buildRustNative) }

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.kotlinx)
    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)
    implementation(libs.koin.annotations)
    implementation(libs.ktorfit.lib)
    ksp(libs.ktorfit.ksp)
    implementation(libs.bundles.coil3)
    implementation(libs.bundles.coil3.extensions)
    implementation(libs.bundles.ktor)
    implementation(libs.molecule.runtime)
    implementation(libs.ksoup)
    implementation(libs.bundles.accompanist)
    lintChecks(libs.compose.lint.checks)
    implementation(libs.composeIcons.fontAwesome)
    implementation(libs.datastore)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.bundles.media3)
    implementation(libs.swiper)
    implementation(libs.reorderable)
    implementation(libs.androidx.window)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.compose.webview)
    implementation(projects.shared)
    implementation(projects.social.bluesky)
    implementation(projects.social.fanbox)
    implementation(projects.social.mastodon)
    implementation(projects.social.misskey)
    implementation(projects.social.nostr)
    implementation(projects.social.pixiv)
    implementation(projects.social.vvo)
    implementation(projects.social.xqt)
    implementation(projects.feature.login)
    implementation(projects.feature.subscription)
    implementation(projects.feature.tab)
    implementation(projects.feature.agent)
    implementation(projects.feature.plugin)
    implementation(projects.composeUi)
    implementation(libs.androidx.splash)
    implementation(libs.materialKolor)
    implementation(libs.colorpicker.compose)
    implementation(libs.material.motion.compose)
    implementation(libs.nestedScrollView)
    implementation(libs.precompose.molecule)
    implementation(libs.webkit)
    implementation(libs.bundles.navigation3)
    implementation(libs.richtext.ui.material3)
    implementation(libs.androidx.browser)

    // START Non-FOSS component
    if (project.file("google-services.json").exists()) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics.ktx)
        implementation(libs.firebase.analytics.ktx)
        implementation(libs.kotlinx.coroutines.play.services)
        implementation("com.google.mlkit:genai-prompt:1.0.0-beta5")
        implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")
    }
    // END Non-FOSS component

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}

// START Non-FOSS component
if (project.file("google-services.json").exists()) {
    afterEvaluate {
        val uploadCrashlyticsMappingFileRelease by tasks
        val processDebugGoogleServices by tasks
        uploadCrashlyticsMappingFileRelease.dependsOn(processDebugGoogleServices)
    }
}
// END Non-FOSS component

abstract class GenerateDeepLinkManifestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val hostsFile: RegularFileProperty
    @get:OutputFile
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun run() {
        val hosts = hostsFile.get().asFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
        val dataTags = hosts.joinToString("\n") { host ->
            """<data android:host="$host" />"""
        }

        manifest.get().asFile.writeText(
            """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application>
    <activity android:name="dev.dimension.flare.MainActivity" android:exported="true">
      <intent-filter android:autoVerify="false">
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
         <data android:scheme="https" />
         $dataTags
      </intent-filter>
    </activity>
  </application>
</manifest>
      """.trimIndent()
        )
    }
}

extensions.getByType(com.android.build.api.variant.AndroidComponentsExtension::class.java)
    .onVariants { variant: com.android.build.api.variant.Variant ->
        val t = tasks.register(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}DeepLinkManifest",
            GenerateDeepLinkManifestTask::class.java
        ) {
            hostsFile = project.layout.projectDirectory.file("deeplink.txt")
        }

        variant.sources.manifests.addGeneratedManifestFile(
            t,
            GenerateDeepLinkManifestTask::manifest
        )
    }

kotlin {
    sourceSets {
        all {
            languageSettings {
                optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            }
        }
    }
}
