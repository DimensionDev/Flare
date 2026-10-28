package dev.dimension.flare.ui.presenter.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.translation.image.ImageTranslationError
import dev.dimension.flare.data.translation.image.ImageTranslationException
import dev.dimension.flare.data.translation.image.ImageTranslationRepository
import dev.dimension.flare.data.translation.image.fingerprint
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8

public data class ImageTranslationItem(
    val loading: Boolean = false,
    val translated: UiMedia.Image? = null,
    val showingTranslated: Boolean = false,
    val error: ImageTranslationError? = null,
    val previous: UiMedia.Image? = null,
    val previousWasShown: Boolean = false,
)

public class ImageTranslationPresenter(
    private val accountType: AccountType? = null,
) : PresenterBase<ImageTranslationPresenter.State>() {
    private val store by koinInject<AppDataStore>()
    private val repository by koinInject<ImageTranslationRepository>()

    public interface State {
        public val configured: Boolean

        public fun item(media: UiMedia): ImageTranslationItem

        public fun displayed(media: UiMedia): UiMedia

        public fun toggle(media: UiMedia)

        public fun regenerate(media: UiMedia)

        public fun clearError(media: UiMedia)

        public fun imageFailed(
            media: UiMedia,
            url: String,
        )
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val session = remember { repository.newSession() }
        val entries = remember { MutableStateFlow<Map<String, ImageTranslationItem>>(emptyMap()) }
        val snapshot = entries.collectAsState().value
        val settings by store.appSettingsStore.data.collectAsState(AppSettings(version = ""))
        val config = settings.imageTranslationConfig
        val target = Locale.language
        val requestIdentity =
            remember(config, target) {
                (config.fingerprint() + target).encodeUtf8().sha256().hex()
            }

        fun key(media: UiMedia) = media.cacheKey + "|" + requestIdentity
        DisposableEffect(session) {
            onDispose {
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    kotlinx.coroutines.withContext(NonCancellable) { repository.closeSession(session) }
                }
            }
        }

        fun request(
            media: UiMedia,
            regenerate: Boolean,
        ) {
            if (media !is UiMedia.Image) return
            val key = key(media)
            while (true) {
                val before = entries.value
                val item = before[key] ?: ImageTranslationItem()
                if (item.loading) return
                if (!regenerate && item.translated != null) {
                    if (entries.compareAndSet(
                            before,
                            before + (key to item.copy(showingTranslated = !item.showingTranslated, error = null)),
                        )
                    ) {
                        return
                    }
                } else {
                    if (entries.compareAndSet(before, before + (key to item.copy(loading = true, error = null)))) break
                }
            }
            scope.launch {
                try {
                    val result =
                        repository.translate(
                            media,
                            (accountType as? AccountType.Specific)?.accountKey,
                            config,
                            target,
                            session,
                            regenerate,
                        )
                    entries.update {
                        val previous = it[key]
                        it + (
                            key to
                                ImageTranslationItem(
                                    translated = result,
                                    showingTranslated = true,
                                    previous = previous?.translated,
                                    previousWasShown = previous?.showingTranslated == true,
                                )
                        )
                    }
                } catch (error: Exception) {
                    if (error is CancellationException && error !is TimeoutCancellationException) {
                        entries.update { state -> state + (key to (state[key] ?: ImageTranslationItem()).copy(loading = false)) }
                        throw error
                    }
                    val reason =
                        when (error) {
                            is ImageTranslationException -> error.reason
                            is TimeoutCancellationException -> ImageTranslationError.Timeout
                            else -> ImageTranslationError.Network
                        }
                    entries.update { state ->
                        state + (key to (state[key] ?: ImageTranslationItem()).copy(loading = false, error = reason))
                    }
                }
            }
        }
        return object : State {
            override val configured = config.configured

            override fun item(media: UiMedia) = snapshot[key(media)] ?: ImageTranslationItem()

            override fun displayed(media: UiMedia): UiMedia =
                item(media).let { if (it.showingTranslated) it.translated ?: media else media }

            override fun toggle(media: UiMedia) = request(media, regenerate = false)

            override fun regenerate(media: UiMedia) = request(media, regenerate = true)

            override fun imageFailed(
                media: UiMedia,
                url: String,
            ) {
                entries.update { state ->
                    val current = state[key(media)]
                    if (current?.translated?.url != url) {
                        state
                    } else {
                        state + (
                            key(media) to
                                ImageTranslationItem(
                                    translated = current.previous,
                                    showingTranslated = current.previousWasShown,
                                    error = ImageTranslationError.InvalidImage,
                                )
                        )
                    }
                }
            }

            override fun clearError(media: UiMedia) {
                entries.update { state -> state + (key(media) to (state[key(media)] ?: ImageTranslationItem()).copy(error = null)) }
            }
        }
    }
}
