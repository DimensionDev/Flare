package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.server.AiTLDRPresenter
import dev.dimension.flare.ui.presenter.server.AiTranslatePresenter
import dev.dimension.flare.ui.presenter.status.TranslatePresenter
import dev.dimension.flare.ui.render.UiRichText

@Composable
internal fun statusTranslatePresenter(
    contentWarning: UiRichText?,
    content: UiRichText,
    targetLanguage: String,
    useAi: Boolean,
): TranslateResult {
    val contentWarningState =
        contentWarning?.takeIf { !it.isEmpty }?.let {
            translateText(it.innerText, targetLanguage, useAi)
        }
    val textState = translateText(content.innerText, targetLanguage, useAi)
    return TranslateResult(
        contentWarning = contentWarningState,
        text = textState,
    )
}

@Composable
private fun translateText(
    text: String,
    targetLanguage: String,
    useAi: Boolean,
) = run {
    remember(text, targetLanguage, useAi) {
        if (useAi) {
            AiTranslatePresenter(text, targetLanguage)
        } else {
            TranslatePresenter(text, targetLanguage)
        }
    }.invoke()
}

@Composable
internal fun statusTldrPresenter(
    contentWarning: UiRichText?,
    content: UiRichText,
    targetLanguage: String,
): UiState<String> =
    remember(contentWarning, content, targetLanguage) {
        val html =
            buildString {
                contentWarning?.takeIf { !it.isEmpty }?.let {
                    append("<content-warning>")
                    append(it.html)
                    append("</content-warning>")
                }
                append("<content>")
                append(content.html)
                append("</content>")
            }
        AiTLDRPresenter(html, targetLanguage)
    }.invoke()
