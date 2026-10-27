package dev.dimension.flare.ui.component.status

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalNetworkImageAllowHardware
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.presenter.status.StatusSharePresenter
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.screen.status.action.StatusShareReplySheet
import dev.dimension.flare.ui.screen.status.action.captureOffscreenShareBitmap
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Instant

@OptIn(ExperimentalComposeUiApi::class)
class StatusShareContentTest {
    @get:Rule val composeRule = createComposeRule()

    companion object {
        private var ownsKoin = false
        private var mediaQueryEnabled = false

        @JvmStatic @BeforeClass
        fun setUpFormatting() {
            mediaQueryEnabled = ComposeUiFlags.isMediaQueryIntegrationEnabled
            ComposeUiFlags.isMediaQueryIntegrationEnabled = true
            if (GlobalContext.getOrNull() == null) {
                startKoin {
                    modules(
                        module {
                            single<PlatformFormatter> {
                                object : PlatformFormatter {
                                    override fun formatNumber(number: Long) = number.toString()

                                    override fun formatRelativeInstant(instant: Instant) = "now"

                                    override fun formatFullInstant(instant: Instant) = "2026-01-01"

                                    override fun formatAbsoluteInstant(instant: Instant) = "2026-01-01"
                                }
                            }
                        },
                    )
                }
                ownsKoin = true
            }
        }

        @JvmStatic @AfterClass
        fun stopFormatting() {
            ComposeUiFlags.isMediaQueryIntegrationEnabled = mediaQueryEnabled
            if (ownsKoin) stopKoin()
        }
    }

    @Test
    fun replyRowExposesOneToggleAndNeverNavigatesThePost() {
        val reply = post("reply", "Selectable reply").copy(user = createSampleUser().copy(avatar = null, banner = null))
        var selected by mutableStateOf(false)
        var confirmed = false
        var cancelled = false
        val state =
            object : StatusSharePresenter.State {
                override val status = UiState.Success<UiTimelineV2>(reply)
                override val replies = persistentListOf<UiTimelineV2>(reply)
                override val selectedReplies = persistentListOf<UiTimelineV2>()
                override val expandableReplies = persistentListOf<MicroBlogKey>()
                override val selecting = true
                override val selectedCount get() = if (selected) 1 else 0
                override val loading = false
                override val error: Throwable? = null
                override val hasMore = false

                override fun isSelected(key: MicroBlogKey) = selected

                override fun toggleReply(key: MicroBlogKey) {
                    selected = !selected
                }

                override fun selectReplies() {}

                override fun clearSelection() {
                    selected = false
                }

                override fun cancelSelection() {
                    cancelled = true
                }

                override fun confirmSelection() {
                    confirmed = true
                }

                override fun loadMore() {}

                override fun loadReplyChildren(key: MicroBlogKey) {}
            }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalGlobalAppearance provides GlobalAppearance(),
                LocalTimelineAppearance provides TimelineAppearance(showTranslateButton = false),
            ) {
                FlareTheme(darkTheme = false) {
                    StatusShareReplySheet(state)
                }
            }
        }
        composeRule
            .onNodeWithContentDescription("Selectable reply", substring = true)
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.runOnIdle { assertTrue(selected) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.externalCacheDir, "selected-reply-picker.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        composeRule.onNodeWithContentDescription("Selectable reply", substring = true).performClick().assertIsOff()
        composeRule.runOnIdle { assertFalse(selected) }
        val selector = composeRule.onNodeWithContentDescription("Selectable reply", substring = true)
        selector.performKeyInput { pressKey(Key.Tab) }
        selector.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        selector.assertIsFocused()
        selector.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Tab)
            keyUp(Key.ShiftLeft)
        }
        composeRule
            .onAllNodes(
                androidx.compose.ui.test
                    .isFocused(),
            ).assertCountEquals(1)
        selector.performKeyInput { pressKey(Key.Tab) }
        selector.assertIsFocused()
        selector.performKeyInput { pressKey(Key.Enter) }
        selector.assertIsOn()
        composeRule.runOnIdle { assertTrue(selected) }
        composeRule.onNodeWithText("Clear").performClick()
        selector.assertIsOff()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun screenshotReplyIgnoresTimelineLineLimitAndDoesNotContainPickerControls() {
        val text = (1..8).joinToString("\n") { "Reply line $it" }
        val root = post("root", "Root post")
        val reply = post("reply", text)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalGlobalAppearance provides GlobalAppearance(),
                LocalTimelineAppearance provides TimelineAppearance(lineLimit = 1, showTranslateButton = false),
            ) {
                FlareTheme(darkTheme = false) {
                    Box(Modifier.width(360.dp)) {
                        StatusShareContent(root, root.statusKey, listOf(reply))
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(8, layouts.single().lineCount)
        assertFalse(layouts.single().hasVisualOverflow)
        composeRule.onAllNodes(isToggleable()).assertCountEquals(0)
        composeRule
            .onAllNodes(
                androidx.compose.ui.test
                    .hasText(root.createdAt.full),
            ).assertCountEquals(1)
    }

    @Test
    fun longReplyExportsBeyondTheScreenHeight() {
        val root = post("root", "Root post")
        val reply = post("reply", (1..100).joinToString("\n") { "Reply line $it" })
        val captured = AtomicReference<Bitmap?>()
        val completed = AtomicBoolean(false)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalGlobalAppearance provides GlobalAppearance(),
                LocalTimelineAppearance provides TimelineAppearance(lineLimit = 1, showTranslateButton = false),
            ) {
                FlareTheme(darkTheme = false) {
                    val view = LocalView.current
                    val parent = rememberCompositionContext()
                    val lifecycle = LocalLifecycleOwner.current
                    val savedState = LocalSavedStateRegistryOwner.current
                    val viewModelStore = LocalViewModelStoreOwner.current
                    LaunchedEffect(Unit) {
                        captured.set(
                            captureOffscreenShareBitmap(
                                context = view.context,
                                view = view,
                                parentCompositionContext = parent,
                                lifecycleOwner = lifecycle,
                                savedStateRegistryOwner = savedState,
                                viewModelStoreOwner = viewModelStore,
                                widthPx = 1080,
                            ) {
                                Surface {
                                    StatusShareContent(root, root.statusKey, listOf(reply))
                                }
                            },
                        )
                        completed.set(true)
                    }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 15_000) { completed.get() }
        val bitmap = requireNotNull(captured.get())
        assertEquals(1080, bitmap.width)
        assertTrue("Long reply must include rows below the screen", bitmap.height > 4096)
        var textPixels = 0
        for (y in bitmap.height - 350 until bitmap.height - 150 step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) == 255 && android.graphics.Color.red(pixel) < 100) textPixels++
            }
        }
        assertTrue("Text near the end of the reply must be painted", textPixels > 100)
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir
        File(directory, "selected-reply-long.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun screenshotWaitsForMediaProxyPreparation() {
        val routeRequested = AtomicBoolean(false)
        val releaseRoute = CompletableDeferred<Unit>()
        val settings = ProxySettings(global = ProxyPolicy(ProxyMode.DIRECT))
        val store =
            object : DataStore<ProxySettings> {
                override val data =
                    flow {
                        routeRequested.set(true)
                        releaseRoute.await()
                        emit(settings)
                    }

                override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
                    error("This fixture does not write proxy settings")
            }
        val koin = requireNotNull(GlobalContext.getOrNull())
        val originalRepository = koin.getOrNull<ProxyRepository>()
        val proxyModule = module { single { ProxyRepository(store) } }
        val targetHits = AtomicInteger()
        val png =
            ByteArrayOutputStream().use { output ->
                val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                try {
                    image.eraseColor(android.graphics.Color.rgb(0, 220, 80))
                    check(image.compress(Bitmap.CompressFormat.PNG, 100, output))
                    output.toByteArray()
                } finally {
                    image.recycle()
                }
            }
        val target = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val server =
            thread(isDaemon = true) {
                while (!target.isClosed) {
                    val socket = runCatching { target.accept() }.getOrNull() ?: break
                    socket.use { client ->
                        runCatching {
                            client.soTimeout = 5_000
                            val input = client.getInputStream().bufferedReader()
                            while (true) {
                                if (input.readLine().isNullOrEmpty()) break
                            }
                            targetHits.incrementAndGet()
                            client.getOutputStream().apply {
                                write(
                                    (
                                        "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${png.size}\r\n" +
                                            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
                                    ).toByteArray(),
                                )
                                write(png)
                                flush()
                            }
                        }
                    }
                }
            }
        val imageUrl = "http://127.0.0.1:${target.localPort}/${UUID.randomUUID()}.png"
        val media =
            UiMedia.Image(
                url = imageUrl,
                previewUrl = imageUrl,
                description = "Proxy preparation fixture",
                height = 32f,
                width = 32f,
                sensitive = false,
                proxy = ProxyRef.Global,
            )
        val root = post("proxy-preparation", "Image capture").copy(images = persistentListOf(media))
        val captured = AtomicReference<Bitmap?>()
        val completed = AtomicInteger()
        var attempt by mutableStateOf(0)
        var mounted by mutableStateOf(true)
        koin.loadModules(listOf(proxyModule))
        try {
            composeRule.setContent {
                if (mounted) {
                    CompositionLocalProvider(
                        LocalGlobalAppearance provides GlobalAppearance(),
                        LocalTimelineAppearance provides TimelineAppearance(showTranslateButton = false),
                    ) {
                        FlareTheme(darkTheme = false) {
                            val view = LocalView.current
                            val parent = rememberCompositionContext()
                            val lifecycle = LocalLifecycleOwner.current
                            val savedState = LocalSavedStateRegistryOwner.current
                            val viewModelStore = LocalViewModelStoreOwner.current
                            LaunchedEffect(attempt) {
                                captured.set(
                                    captureOffscreenShareBitmap(
                                        context = view.context,
                                        view = view,
                                        parentCompositionContext = parent,
                                        lifecycleOwner = lifecycle,
                                        savedStateRegistryOwner = savedState,
                                        viewModelStoreOwner = viewModelStore,
                                        widthPx = 1080,
                                    ) {
                                        CompositionLocalProvider(LocalNetworkImageAllowHardware provides false) {
                                            Surface { StatusShareContent(root, root.statusKey, emptyList()) }
                                        }
                                    },
                                )
                                completed.incrementAndGet()
                            }
                        }
                    }
                }
            }
            composeRule.waitUntil(timeoutMillis = 5_000) { routeRequested.get() }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
            val finishedBeforeRouteReady = completed.get() > 0
            assertEquals("No image request may start before its route is ready", 0, targetHits.get())
            releaseRoute.complete(Unit)
            // Let a failing first capture retry so this regression also proves real PNG loading.
            if (finishedBeforeRouteReady) composeRule.runOnIdle { attempt++ }
            composeRule.waitUntil(timeoutMillis = 20_000) { completed.get() > if (finishedBeforeRouteReady) 1 else 0 }
            val bitmap = requireNotNull(captured.get()) { "The capture must succeed after routing becomes available" }
            try {
                assertTrue("The real image endpoint must be requested", targetHits.get() > 0)
                var imagePixels = 0
                for (y in 0 until bitmap.height step 4) {
                    for (x in 0 until bitmap.width step 4) {
                        val color = bitmap.getPixel(x, y)
                        if (android.graphics.Color.green(color) > 180 && android.graphics.Color.red(color) < 30) imagePixels++
                    }
                }
                assertTrue("The capture must contain the downloaded PNG", imagePixels > 100)
                assertFalse("Capture must remain pending while its image route is being prepared", finishedBeforeRouteReady)
            } finally {
                bitmap.recycle()
            }
        } finally {
            releaseRoute.complete(Unit)
            try {
                composeRule.runOnIdle { mounted = false }
            } finally {
                koin.unloadModules(listOf(proxyModule))
                originalRepository?.let { original -> koin.loadModules(listOf(module { single { original } })) }
                target.close()
                server.join(1_000)
            }
        }
    }

    private fun post(
        id: String,
        text: String,
    ) = UiTimelineV2.Post(
        platformId = "Mastodon",
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = null,
        content = UiTranslatableText(text.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id, "example.invalid"),
        card = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z").toUi(),
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Guest,
    )
}
