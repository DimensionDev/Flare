package dev.dimension.flare.ui.presenter.login

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LoginSuccessDispatcherTest {
    @BeforeTest
    fun setUp() {
        val registry =
            LoginPlatformRegistry(
                PlatformRegistry(PlatformRuntimeData(listOf(TestLoginPlatform), emptyList())),
            )
        startKoin { modules(module { single { registry } }) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun reloginSuccessReturnsToMainFromBackgroundLoginHandler() =
        assertSuccessOnMain { onSuccess ->
            val target = ReloginTarget(MicroBlogKey("account", "example.invalid"), "Test")
            val state =
                moleculeFlow(RecompositionMode.Immediate) {
                    ReloginPresenter(target, onSuccess).body()
                }.first()
            state.createLoginHandler(LoginMethodType.Password)
        }

    @Test
    fun newLoginSuccessReturnsToMainFromBackgroundLoginHandler() =
        assertSuccessOnMain { onSuccess ->
            val state =
                moleculeFlow(RecompositionMode.Immediate) {
                    ServiceSelectPresenter(onSuccess).body()
                }.first()
            state.createLoginHandler("Test", "example.invalid", LoginMethodType.Password, null)
        }

    private fun assertSuccessOnMain(createHandler: suspend (() -> Unit) -> LoginMethodHandler) =
        runTest {
            Executors.newSingleThreadExecutor { Thread(it, "login-ui") }.asCoroutineDispatcher().use { main ->
                Dispatchers.setMain(main)
                try {
                    var callbackThread: String? = null
                    var callbacks = 0
                    val handler =
                        createHandler {
                            callbackThread = Thread.currentThread().name
                            callbacks += 1
                        }
                    withContext(Dispatchers.Default) {
                        handler.perform("login")
                    }
                    assertEquals("login-ui", callbackThread)
                    assertEquals(1, callbacks)
                    handler.close()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
}

private object TestLoginPlatform : PlatformSpec, LoginPlatformProvider {
    override val platformId = "Test"
    override val metadata = PlatformMetadata("Test", UiIcon.Mastodon)
    override val isDefaultGuest = true
    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = persistentListOf()
    override val methods = listOf(LoginMethodSpec(LoginMethodType.Password, UiStrings.PasswordLogin))
    override val detector =
        object : PlatformDetector {
            override val priority = 0

            override suspend fun detect(host: String): NodeDetection? = null
        }

    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> = emptyList()

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata = error("Not used")

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = error("Not used")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = error("Not used")

    override fun createHandler(context: LoginContext): LoginMethodHandler =
        object : LoginMethodHandler {
            override val state = MutableStateFlow(LoginFlowState())
            override val effects = emptyFlow<LoginEffect>()

            override suspend fun perform(actionId: String) = context.onSuccess()

            override suspend fun resume(value: String) = context.onSuccess()

            override fun updateField(
                id: String,
                value: String,
            ) = Unit

            override fun clear() = Unit
        }
}
