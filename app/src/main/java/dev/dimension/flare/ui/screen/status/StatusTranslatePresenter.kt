package dev.dimension.flare.ui.screen.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import moe.tlaster.ktml.dom.Element

@Composable
fun statusTranslatePresenter(
    contentWarning: String?,
    content: Element,
): TranslateResult {
    val text = content.innerText
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
        val language by produceState<UiState<String>>(initialValue = UiState.Loading(), key1 = text) {
            LanguageIdentification.getClient()
                .identifyLanguage(text)
                .addOnSuccessListener {
                    value =
                        if (it == "und") {
                            UiState.Error(Exception("Language not supported"))
                        } else {
                            UiState.Success(it)
                        }
                }
                .addOnFailureListener {
                    value = UiState.Error(it)
                }
        }
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
                val client =
                    remember(source, target) {
                        val options =
                            TranslatorOptions.Builder()
                                .setSourceLanguage(source)
                                .setTargetLanguage(target)
                                .build()
                        Translation.getClient(options)
                    }
                val state by produceState<UiState<String>>(
                    initialValue = UiState.Loading(),
                    key1 = text,
                    key2 = source,
                    key3 = target,
                ) {
                    client.downloadModelIfNeeded()
                        .addOnSuccessListener {
                            client.translate(text)
                                .addOnSuccessListener {
                                    value = UiState.Success(it)
                                }
                                .addOnFailureListener {
                                    value = UiState.Error(it)
                                }
                        }
                        .addOnFailureListener {
                            value = UiState.Error(it)
                        }
                }
                state
            }
        }
    }

data class TranslateResult(
    val contentWarning: UiState<String>?,
    val text: UiState<String>,
)
