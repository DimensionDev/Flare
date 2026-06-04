import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.ksp)
}

val webPresenterManifest = layout.buildDirectory.file("generated/web-presenters/manifest/flare-web-presenters.json")
val webPresenterTsDir = layout.buildDirectory.dir("generated/web-presenters/ts")
val webPresenterTsGenerator = rootProject.layout.projectDirectory.file("web/scripts/generate-presenters.mjs")

kotlin {
    flare {
        namespace = "dev.dimension.flare.web.shared"
        platforms(
            FlarePlatform.WEB,
        )
        ksp(projects.webPresenterProcessor)
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("web-shared")
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(projects.shared)
                implementation(projects.feature.login)
                implementation(projects.feature.subscription)
                implementation(projects.social.bluesky)
                implementation(projects.social.mastodon)
                implementation(projects.social.misskey)
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.immutable)
                implementation(libs.kotlinx.serialization.json)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
            }
        }
    }
}

ksp {
    arg("webPresenterManifestPath", webPresenterManifest.get().asFile.absolutePath)
    arg(
        "webPresenterTypes",
        listOf(
            "dev.dimension.flare.ui.presenter.EnvironmentSettingsPresenter",
            "dev.dimension.flare.ui.presenter.SettingsPresenter",
            "dev.dimension.flare.ui.presenter.WebDataTransferPresenter",
            "dev.dimension.flare.ui.presenter.WebLocalFilterPresenter",
            "dev.dimension.flare.ui.presenter.home.rss.EditRssSourcePresenter",
            "dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter",
            "dev.dimension.flare.ui.presenter.settings.AiConfigPresenter",
            "dev.dimension.flare.ui.presenter.settings.AiTranslationTestPresenter",
            "dev.dimension.flare.ui.presenter.settings.AppearancePresenter",
            "dev.dimension.flare.ui.presenter.settings.AccountsPresenter",
            "dev.dimension.flare.ui.presenter.settings.StoragePresenter",
            "dev.dimension.flare.ui.presenter.settings.DevModePresenter",
            "dev.dimension.flare.ui.presenter.settings.LocalCacheSearchPresenter",
            "dev.dimension.flare.ui.presenter.login.NodeInfoPresenter",
            "dev.dimension.flare.ui.presenter.login.WebLoginFlowPresenter",
            "dev.dimension.flare.ui.presenter.HomeTabsPresenter",
            "dev.dimension.flare.ui.presenter.HomeTimelineWithTabsPresenter",
            "dev.dimension.flare.ui.presenter.home.WebDeepLinkPresenter",
            "dev.dimension.flare.ui.presenter.home.DiscoverPresenter",
            "dev.dimension.flare.ui.presenter.home.SearchPresenter",
            "dev.dimension.flare.ui.presenter.home.AllNotificationPresenter",
            "dev.dimension.flare.ui.presenter.home.AllNotificationBadgePresenter",
            "dev.dimension.flare.ui.presenter.home.SecondaryTabsPresenter",
            "dev.dimension.flare.ui.presenter.home.TimelinePresenter",
            "dev.dimension.flare.ui.presenter.profile.ProfilePresenter",
            "dev.dimension.flare.ui.presenter.profile.ProfileWithUserNameAndHostPresenter",
            "dev.dimension.flare.ui.presenter.status.StatusContextPresenter",
        ).joinToString(","),
    )
}

val generateWebPresenterTs by tasks.registering(Exec::class) {
    dependsOn(tasks.named("kspKotlinWasmJs"))

    inputs.file(webPresenterManifest)
    inputs.file(webPresenterTsGenerator)
    outputs.dir(webPresenterTsDir)

    commandLine(
        "node",
        webPresenterTsGenerator.asFile.absolutePath,
        webPresenterManifest.get().asFile.absolutePath,
        webPresenterTsDir.get().asFile.absolutePath,
    )
}

tasks.matching {
    it.name == "wasmJsBrowserDevelopmentLibraryDistribution" ||
        it.name == "wasmJsBrowserProductionLibraryDistribution"
}.configureEach {
    dependsOn(generateWebPresenterTs)
}
