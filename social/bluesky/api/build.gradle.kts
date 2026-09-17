import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.SelectLexiconsTask
import dev.dimension.flare.buildlogic.flare
import sh.christian.ozone.api.generator.ApiReturnType

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ozone.generator)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.social.bluesky.api"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.WEB,
            FlarePlatform.MACOS,
        )
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val blueskyLexicons by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

val selectLexicons by tasks.registering(SelectLexiconsTask::class) {
    schemaArchives.from(blueskyLexicons)
    rootsFile.set(layout.projectDirectory.file("lexicon-roots.txt"))
    outputDirectory.set(layout.buildDirectory.dir("selected-lexicons"))
}

dependencies {
    blueskyLexicons(libs.bluesky.lexicons)
    lexicons(files(selectLexicons).asFileTree.matching { include("**/*.json") })
}

lexicons {
    namespace.set("sh.christian.ozone.api.xrpc")
    defaults {
        generateUnknownsForSealedTypes.set(true)
        generateUnknownsForEnums.set(true)
    }
    generateApi("BlueskyApi") {
        packageName.set("sh.christian.ozone")
        withKtorImplementation("XrpcBlueskyApi")
        returnType.set(ApiReturnType.Response)
    }
}

tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
    dependsOn(tasks.named("generateLexicons"))
}
