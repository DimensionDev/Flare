package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.server.AiTLDRPresenter
import dev.dimension.flare.ui.presenter.status.TranslatePresenter
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toTranslatableText

@Composable
internal fun statusTranslatePresenter(
    contentWarning: UiRichText?,
    content: UiRichText,
    targetLanguage: String,
): TranslateResult {
    val contentWarningState =
        contentWarning?.takeIf { !it.isEmpty }?.let {
            translateText(it, targetLanguage)
        }
    val textState = translateText(content, targetLanguage)
    return TranslateResult(
        contentWarning = contentWarningState,
        text = textState,
    )
}

@Composable
private fun translateText(
    text: UiRichText,
    targetLanguage: String,
) = run {
    remember(text, targetLanguage) {
        TranslatePresenter(text, targetLanguage)
    }.invoke()
}

@Composable
internal fun statusTldrPresenter(
    contentWarning: UiRichText?,
    content: UiRichText,
    targetLanguage: String,
): UiState<String> =
    remember(contentWarning, content, targetLanguage) {
        val text =
            buildString {
                contentWarning?.takeIf { !it.isEmpty }?.let {
                    append("Content warning:\n")
                    append(it.toTranslatableText())
                    append("\n\n")
                }
                append("Content:\n")
                append(content.toTranslatableText())
            }
        AiTLDRPresenter(text, targetLanguage)
    }.invoke()
