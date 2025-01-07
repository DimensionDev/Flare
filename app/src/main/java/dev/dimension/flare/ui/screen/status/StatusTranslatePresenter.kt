package dev.dimension.flare.ui.screen.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.fleeksoft.ksoup.nodes.Element
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.TranslatePresenter
import dev.dimension.flare.ui.render.UiRichText

@Composable
fun statusTranslatePresenter(
    contentWarning: UiRichText?,
    content: Element,
): TranslateResult {
    val text = content.text()
    val contentWarningState =
        contentWarning?.let {
            translateText(it.innerText)
        }
    val textState = translateText(text)
    return TranslateResult(
        contentWarning = contentWarningState,
        text = textState,
    )
}

@Composable
private fun translateText(text: String) =
    run {
        remember(text) {
            TranslatePresenter(text)
        }.invoke()
    }
