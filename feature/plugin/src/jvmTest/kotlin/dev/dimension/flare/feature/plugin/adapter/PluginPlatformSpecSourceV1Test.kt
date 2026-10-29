package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.feature.plugin.host.PluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginTransportRequestV1
import dev.dimension.flare.feature.plugin.host.PluginTransportResponseV1
import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.login.PluginFormLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingV1
import dev.dimension.flare.feature.plugin.login.PluginWebCookieLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeUnavailableException
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PluginPlatformSpecSourceV1Test {
    @Test
    fun detectorImagesRequireTheRunningPackagesMediaPermission() =
        runBlocking {
            for (image in listOf("iconUrl", "bannerUrl")) {
                withPlatform(mediaNetwork = false, metadata = "$image: request.origin + '/image.png'") { platform ->
                    assertFailsWith<PluginRuntimeUnavailableException> { platform.detector.detect("instance.example") }
                    val error = assertFailsWith<PluginRuntimeUnavailableException> { platform.instanceMetadata("instance.example") }
                    assertEquals("Plugin has no media network permission", assertIs<IllegalArgumentException>(error.cause).message)
                }
            }
        }

    @Test
    fun grantedDetectorImagesAndTextOnlyInstancesRemainAvailable() =
        runBlocking {
            withPlatform(
                mediaNetwork = true,
                metadata = "iconUrl: request.origin + '/icon.png', bannerUrl: request.origin + '/banner.png'",
            ) { platform ->
                assertNotNull(platform.detector.detect("instance.example"))
                val instance = platform.instanceMetadata("instance.example").instance
                assertEquals("https://instance.example/icon.png", instance.iconUrl)
                assertEquals("https://instance.example/banner.png", instance.bannerUrl)
            }
            withPlatform(mediaNetwork = false, metadata = "description: 'Text only'") { platform ->
                assertNotNull(platform.detector.detect("instance.example"))
                val instance = platform.instanceMetadata("instance.example").instance
                assertEquals("Text only", instance.description)
                assertNull(instance.iconUrl)
                assertNull(instance.bannerUrl)
            }
        }

    private suspend fun withPlatform(
        mediaNetwork: Boolean,
        metadata: String,
        block: suspend (LoginPlatformProvider) -> Unit,
    ) {
        val root = Files.createTempDirectory("plugin-detector-media").toOkioPath()
        val files = FileSystem.SYSTEM
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pool =
            PluginRuntimePool(
                files,
                object : PluginHttpTransport {
                    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 =
                        error("The detector does not need HTTP")
                },
            )
        try {
            val archive = root / "plugin.fpp"
            val manifest =
                TestFppFactory.validManifest.replace(
                    "\"platform\": {",
                    "\"permissions\": {\"mediaNetwork\": $mediaNetwork}, \"platform\": {",
                )
            val script =
                TestFppFactory.validScript.replace(
                    "return { matched: true };",
                    "return { match: 'exact', canonicalOrigin: request.origin, instance: { domain: 'instance.example', $metadata } };",
                )
            TestFppFactory.write(archive, TestFppFactory.validEntries(manifest = manifest, script = script))
            val store = PluginStateStore.open(files, root / "state")
            val installer = PluginInstaller(files, store)
            installer.commit(installer.inspect(archive), confirmed = true)
            val snapshot = PluginStateStore.open(files, root / "state").running
            val pending =
                object : PluginOAuthPendingStoreV1 {
                    override suspend fun save(pending: PluginOAuthPendingV1): Unit = error("Unused")

                    override suspend fun load(flowId: String): PluginOAuthPendingV1? = null

                    override suspend fun consume(pending: PluginOAuthPendingV1): Boolean = false
                }
            val platform =
                PluginPlatformSpecSourceV1(
                    snapshot,
                    pool,
                    PluginOAuthLoginCoordinatorV1(pool, pending, runningPlugin = snapshot.plugins::get),
                    PluginFormLoginCoordinatorV1(pool),
                    PluginWebCookieLoginCoordinatorV1(pool),
                    scope,
                ).load(emptySet()).single() as LoginPlatformProvider
            block(platform)
        } finally {
            scope.cancel()
            pool.close()
            files.deleteRecursively(root, mustExist = false)
        }
    }
}
