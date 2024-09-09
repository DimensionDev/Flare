package dev.dimension.flare.ui.screen.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.fleeksoft.ksoup.nodes.Element
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

@Composable
fun statusTranslatePresenter(
    contentWarning: String?,
    content: Element,
): TranslateResult {
    val text = content.text()
    val contentWarningState =
        contentWarning?.let {
            translateText(it)
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
        val language by remember(text) {
            flow<UiState<String>> {
                runCatching {
                    LanguageIdentification
                        .getClient()
                        .identifyLanguage(text)
                        .await()
                }.fold(
                    onSuccess = { emit(UiState.Success(it)) },
                    onFailure = { emit(UiState.Error(it)) },
                )
            }
        }.collectAsState(UiState.Loading())
        language.flatMap {
            val source =
                remember(it) {
                    TranslateLanguage.fromLanguageTag(it)
                }
            val locale = androidx.compose.ui.text.intl.Locale.current
            val target =
                remember {
                    TranslateLanguage.fromLanguageTag(locale.language)
                }
            if (source == target || source == null || target == null) {
                UiState.Success(text)
            } else {
                remember(source, target) {
                    val options =
                        TranslatorOptions
                            .Builder()
                            .setSourceLanguage(source)
                            .setTargetLanguage(target)
                            .build()
                    val client =
                        Translation.getClient(options)
                    flow<UiState<String>> {
                        runCatching {
                            client
                                .downloadModelIfNeeded()
                                .await()
                            client
                                .translate(text)
                                .await()
                        }.fold(
                            onSuccess = { emit(UiState.Success(it)) },
                            onFailure = { emit(UiState.Error(it)) },
                        )
                    }
                }.collectAsState(UiState.Loading()).value
            }
        }
    }
