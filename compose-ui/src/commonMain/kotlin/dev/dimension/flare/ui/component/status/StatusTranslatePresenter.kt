package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.server.AiTLDRPresenter
import dev.dimension.flare.ui.presenter.status.StatusTranslationPayload
import dev.dimension.flare.ui.presenter.status.TranslateCacheTarget
import dev.dimension.flare.ui.presenter.status.TranslatePresenter
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toTranslatableText

@Composable
internal fun statusTranslatePresenter(
    item: UiTimelineV2.Post,
    contentWarning: UiRichText?,
    content: UiRichText,
    targetLanguage: String,
): TranslateResult {
    val contentWarningState =
        contentWarning?.takeIf { !it.isEmpty }?.let {
            translateText(
                text = it,
                targetLanguage = targetLanguage,
                cacheTarget =
                    TranslateCacheTarget(
                        accountType = item.accountType,
                        statusKey = item.statusKey,
                        payload =
                            StatusTranslationPayload(
                                content = content,
                                contentWarning = contentWarning,
                            ),
                        field = TranslateCacheTarget.Field.ContentWarning,
                    ),
            )
        }
    val textState =
        translateText(
            text = content,
            targetLanguage = targetLanguage,
            cacheTarget =
                TranslateCacheTarget(
                    accountType = item.accountType,
                    statusKey = item.statusKey,
                    payload =
                        StatusTranslationPayload(
                            content = content,
                            contentWarning = contentWarning,
                        ),
                    field = TranslateCacheTarget.Field.Content,
                ),
        )
    return TranslateResult(
        contentWarning = contentWarningState,
        text = textState,
    )
}

@Composable
private fun translateText(
    text: UiRichText,
    targetLanguage: String,
    cacheTarget: TranslateCacheTarget? = null,
) = run {
    remember(text, targetLanguage, cacheTarget) {
        TranslatePresenter(text, targetLanguage, cacheTarget)
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
