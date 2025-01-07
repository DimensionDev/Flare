package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

public class TranslatePresenter(
    private val source: String,
    private val targetLanguage: String = Locale.language,
) : PresenterBase<UiState<String>>() {
    @Composable
    override fun body(): UiState<String> {
        val baseUrl = "https://translate.google.com/translate_a/single"
        return produceState(UiState.Loading()) {
            val response =
                ktorClient()
                    .get {
                        url(baseUrl)
                        parameter("client", "gtx")
                        parameter("sl", "auto")
                        parameter("tl", targetLanguage)
                        parameter("dt", "t")
                        parameter("q", source)
                        parameter("ie", "UTF-8")
                        parameter("oe", "UTF-8")
                    }.body<JsonArray>()
            val result =
                buildString {
                    response.firstOrNull()?.jsonArray?.forEach {
                        it.jsonArray.firstOrNull()?.let {
                            val content = it.jsonPrimitive.content
                            if (content.isNotEmpty()) {
                                append(content)
                                append("\n")
                            }
                        }
                    }
                }
            value = UiState.Success(result)
        }.value
    }
}
