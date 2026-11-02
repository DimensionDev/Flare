package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.ui.model.withProxy
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.route.DeeplinkRoute
import java.io.File
import java.net.URLClassLoader
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CachedVvoMediaLinkTest {
    @Test
    fun cachedImageLinksWorkWithANewProcessSigningKey() {
        val text =
            renderVVOText(
                """<a href="https://$vvoHostShort/sinaurl?u=https://wx1.sinaimg.cn/large/photo.jpg">查看图片</a>""",
                MicroBlogKey("viewer", "weibo.cn"),
            )
        val cached = JSON.encodeToString(text)
        val originalLink = CachedVvoMediaLinkReader.restore(cached)
        // Isolate Kotlin singletons as on restart, while keeping the serialized cache bytes unchanged.
        val classpath =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it).toURI().toURL() }
                .toTypedArray()
        URLClassLoader(classpath, ClassLoader.getPlatformClassLoader()).use { restarted ->
            val restoredLink =
                restarted
                    .loadClass(CachedVvoMediaLinkReader::class.java.name)
                    .getMethod("restore", String::class.java)
                    .invoke(null, cached) as String
            assertNotEquals(originalLink, restoredLink)
            assertNull(DeeplinkRoute.parse(restoredLink), "A different process must still reject the regenerated signature")
        }
    }
}

object CachedVvoMediaLinkReader {
    @JvmStatic
    fun restore(cached: String): String {
        val proxy = ProxyRef.Account(MicroBlogKey("viewer", "weibo.cn"))
        val text = JSON.decodeFromString<UiRichText>(cached).withProxy(proxy)
        val block = text.renderRuns.single() as RenderContent.Text
        val run = block.runs.single() as RenderRun.Text
        val link = requireNotNull(run.style.resolvedLink)
        val route = DeeplinkRoute.parse(link) as DeeplinkRoute.Media.Image
        check(route.uri == "https://wx1.sinaimg.cn/large/photo.jpg")
        check(route.proxy == proxy)
        return link
    }
}
