import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare
import java.util.Properties

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    id("com.github.gmazzo.buildconfig") version "6.0.10"
}

val secretProperties =
    Properties().apply {
        val file = rootProject.file("secret.properties")
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }

fun tumblrSecret(
    environmentName: String,
    propertyName: String,
    defaultValue: String = "",
) = providers
    .environmentVariable(environmentName)
    .orElse(
        providers.provider {
            secretProperties.getProperty(propertyName) ?: defaultValue
        },
    )

val tumblrClientId = tumblrSecret("TUMBLR_CLIENT_ID", "tumblr.clientId")
val tumblrClientSecret = tumblrSecret("TUMBLR_CLIENT_SECRET", "tumblr.clientSecret")
val tumblrRedirectUri =
    tumblrSecret(
        environmentName = "TUMBLR_REDIRECT_URI",
        propertyName = "tumblr.redirectUri",
        defaultValue = "https://flareapp.moe/tumblr-callback.html",
    )

kotlin {
    flare {
        namespace = "dev.dimension.flare.social.tumblr"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.MACOS,
        )
        ksp(libs.ktorfit.ksp)
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.shared)
                api(projects.feature.loginApi)
                implementation(libs.bundles.kotlinx)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.bundles.ktorfit)
                implementation(libs.bundles.ktor)
                implementation(libs.ktor.client.resources)
                implementation(libs.okio)
                implementation(libs.kotlin.codepoints.deluxe)
                implementation(libs.paging.common)
                implementation(libs.ksoup)
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
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

buildConfig {
    packageName("dev.dimension.flare.social.tumblr")
    className("TumblrBuildConfig")
    useKotlinOutput()
    buildConfigField("clientId", tumblrClientId)
    buildConfigField("clientSecret", tumblrClientSecret)
    buildConfigField("redirectUri", tumblrRedirectUri)
    buildConfigField("configured") {
        type("kotlin.Boolean")
        expression(
            providers.provider {
                (tumblrClientId.get().isNotBlank() && tumblrClientSecret.get().isNotBlank()).toString()
            },
        )
    }
}

val checkTumblrSecrets by tasks.registering {
    group = "verification"
    description = "Checks Tumblr OAuth build secrets for release builds."
    doLast {
        val missing =
            buildList {
                if (tumblrClientId.get().isBlank()) add("TUMBLR_CLIENT_ID or tumblr.clientId")
                if (tumblrClientSecret.get().isBlank()) add("TUMBLR_CLIENT_SECRET or tumblr.clientSecret")
                if (tumblrRedirectUri.get().isBlank()) add("TUMBLR_REDIRECT_URI or tumblr.redirectUri")
            }
        if (missing.isNotEmpty()) {
            throw GradleException("Missing Tumblr OAuth secret(s): ${missing.joinToString()}")
        }
    }
}

tasks.configureEach {
    if (name.contains("Release")) {
        dependsOn(checkTumblrSecrets)
    }
}
