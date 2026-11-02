package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.mutableStateOf
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.ui.model.UiTimelineV2.Post.Visibility
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeVisibilityTest {
    private val weibo =
        ComposeConfig.Visibility(
            allVisibilities = persistentListOf(Visibility.Public, Visibility.Friends, Visibility.SelfOnly),
        )

    @Test
    fun privateSelectionFallsBackWhenCapabilitiesChange() =
        runTest {
            val config = mutableStateOf<ComposeConfig.Visibility?>(weibo)
            var state: VisibilityState? = null
            backgroundScope.launch {
                moleculeFlow(RecompositionMode.Immediate) {
                    rememberComposeVisibilityState(config.value, null)
                }.collect { state = it }
            }
            runCurrent()
            assertNotNull(state).setVisibility(Visibility.SelfOnly)
            runCurrent()
            config.value = ComposeConfig.Visibility(defaultVisibility = Visibility.Followers)
            runCurrent()
            assertEquals(Visibility.Followers, assertNotNull(state).visibility)
            config.value = null
            runCurrent()
            assertTrue(assertNotNull(state).allVisibilities.isEmpty())
            config.value = weibo
            runCurrent()
            assertEquals(Visibility.Public, assertNotNull(state).visibility)
        }

    @Test
    fun rememberedPrivateAudienceResetsAfterIncompatibleAccounts() =
        runTest {
            val config = mutableStateOf<ComposeConfig.Visibility?>(null)
            var state: VisibilityState? = null
            backgroundScope.launch {
                moleculeFlow(RecompositionMode.Immediate) {
                    rememberComposeVisibilityState(config.value, Visibility.SelfOnly)
                }.collect { state = it }
            }
            runCurrent()
            assertEquals(Visibility.Public, assertNotNull(state).visibility)
            config.value = weibo
            runCurrent()
            assertEquals(Visibility.SelfOnly, assertNotNull(state).visibility)
            config.value = null
            runCurrent()
            assertTrue(assertNotNull(state).allVisibilities.isEmpty())
            config.value = weibo
            runCurrent()
            assertEquals(Visibility.Public, assertNotNull(state).visibility)
        }

    @Test
    fun privateDraftVisibilitiesSurviveSerializationAndRestore() {
        for (visibility in listOf(Visibility.SelfOnly, Visibility.Friends, null)) {
            val original = ComposeData("private draft", visibility = visibility)
            val restored =
                original
                    .toDraftContent()
                    .encodeJson()
                    .decodeJson<DraftContent>()
                    .toComposeData(emptyList())
            assertEquals(original, restored)
        }
    }
}
