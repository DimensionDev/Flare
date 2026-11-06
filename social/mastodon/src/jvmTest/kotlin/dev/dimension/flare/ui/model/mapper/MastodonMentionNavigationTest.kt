package dev.dimension.flare.ui.model.mapper

import androidx.datastore.core.DataStore
import com.sun.net.httpserver.HttpServer
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datasource.mastodon.MastodonLoader
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Mention
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.onClicked
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.plugin.module.dsl.modules
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant

class MastodonMentionNavigationTest {
    @Test
    fun clickingMentionsLooksUpTheCanonicalAccountOnTheViewersInstance() =
        runBlocking {
            val viewer = MicroBlogKey("viewer", "home.example")
            val mentions =
                listOf(
                    Mention("200", "bob", "https://social.remote.example/users/bob", "bob@remote.example"),
                    Mention("201", "bob", "https://home.example/@bob", "bob"),
                    Mention("202", "bob", "https://social.remote.example/users/other-bob", "bob@alias.example"),
                )
            val accts = listOf("bob@remote.example", "bob@home.example", "bob@alias.example")
            val targets =
                accts
                    .mapIndexed { index, acct ->
                        acct to Account(id = "resolved-$index", username = "Bob", acct = acct.replaceFirst("bob", "Bob"))
                    }.toMap()
            val requests = ConcurrentLinkedQueue<Url>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                val url = Url("http://127.0.0.1${exchange.requestURI}")
                requests.add(url)
                val account = targets[url.parameters["acct"]]
                val body = account?.let { JSON.encodeToString(Account.serializer(), it) } ?: "{}"
                val bytes = body.encodeToByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(if (account != null) 200 else 404, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            val settings = MutableStateFlow(ProxySettings(global = ProxyPolicy(ProxyMode.DIRECT)))
            val proxies =
                ProxyRepository(
                    object : DataStore<ProxySettings> {
                        override val data = settings

                        override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
                            transform(settings.value).also { settings.value = it }
                    },
                )
            startKoin {
                modules(MastodonRenderTestModule::class)
                modules(module { single { proxies } })
            }
            server.start()
            try {
                val service = MastodonService("http://127.0.0.1:${server.address.port}/", flowOf(""))
                for ((index, mention) in mentions.withIndex()) {
                    val post =
                        assertIs<UiTimelineV2.Post>(
                            Status(
                                id = "post",
                                account = Account(id = "author", username = "author", acct = "author"),
                                createdAt = Instant.parse("2026-10-27T05:37:19Z"),
                                content = """<p><a href="${mention.url}" class="mention">@bob</a></p>""",
                                mentions = listOf(mention),
                            ).render(viewer.host, viewer),
                        )
                    val link =
                        post.content.original.renderRuns
                            .filterIsInstance<RenderContent.Text>()
                            .flatMap { it.runs }
                            .filterIsInstance<RenderRun.Text>()
                            .mapNotNull { it.style.link }
                            .single()
                    var opened: String? = null
                    ClickEvent.Deeplink(link).onClicked(ClickContext { opened = it })
                    val route = assertIs<DeeplinkRoute.Profile.UserNameWithHost>(DeeplinkRoute.parse(assertNotNull(opened)))
                    assertEquals(AccountType.Specific(viewer), route.accountType)

                    val loader = MastodonLoader(assertIs<AccountType.Specific>(route.accountType).accountKey, service)
                    val profile = loader.userByHandleAndHost(UiHandle(route.userName, route.host))

                    assertEquals(MicroBlogKey("resolved-$index", viewer.host), profile.key)
                    assertEquals("@${targets.getValue(accts[index]).acct}", profile.handle.canonical)
                }
                assertEquals(List(3) { "/api/v1/accounts/lookup" }, requests.map { it.encodedPath })
                assertEquals(accts, requests.map { it.parameters["acct"] })
            } finally {
                server.stop(0)
                proxies.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
                stopKoin()
            }
        }
}
