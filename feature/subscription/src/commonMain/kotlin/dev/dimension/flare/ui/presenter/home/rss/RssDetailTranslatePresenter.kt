package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.translation.HtmlArticleTranslationService
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.di.koinInject

/**
 * Presenter that translates the HTML content of an RSS article
 * by translating individual text nodes in the HTML tree.
 * This preserves the original HTML structure (headings, lists, images, etc.)
 * while replacing the text content with translations.
 */
public class RssDetailTranslatePresenter(
    private val htmlContent: String,
    private val title: String,
    private val targetLanguage: String = Locale.language,
) : PresenterBase<RssDetailTranslatePresenter.State>() {
    private val translationService: HtmlArticleTranslationService by koinInject()

    @Immutable
    public interface State {
        public val translatedHtml: UiState<String>
        public val translatedTitle: UiState<String>
    }

    @Composable
    override fun body(): State {
        val result =
            produceState<Pair<UiState<String>, UiState<String>>>(
                initialValue = UiState.Loading<String>() to UiState.Loading<String>(),
            ) {
                value = translationService.translate(htmlContent, title, targetLanguage)
            }
        return object : State {
            override val translatedHtml = result.value.first
            override val translatedTitle = result.value.second
        }
    }
}
