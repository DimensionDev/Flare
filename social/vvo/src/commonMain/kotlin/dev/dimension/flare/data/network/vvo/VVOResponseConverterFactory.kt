package dev.dimension.flare.data.network.vvo

import com.fleeksoft.ksoup.Ksoup
import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.converter.Converter
import de.jensklingenberg.ktorfit.converter.KtorfitResult
import de.jensklingenberg.ktorfit.converter.TypeData
import dev.dimension.flare.data.network.vvo.model.HotflowChildData
import dev.dimension.flare.data.network.vvo.model.VVOResponse
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException

internal class VVOResponseConverterFactory : Converter.Factory {
    override fun suspendResponseConverter(
        typeData: TypeData,
        ktorfit: Ktorfit,
    ): Converter.SuspendResponseConverter<HttpResponse, *>? {
        val emptyValueFactory: (() -> Any) =
            when (typeData.typeInfo.type) {
                VVOResponse::class -> {
                    { VVOResponse<Any?>() }
                }

                HotflowChildData::class -> {
                    { HotflowChildData() }
                }

                else -> {
                    return null
                }
            }

        return VVOResponseConverter(
            typeData = typeData,
            emptyValueFactory = emptyValueFactory,
        )
    }
}

private class VVOResponseConverter(
    private val typeData: TypeData,
    private val emptyValueFactory: () -> Any,
) : Converter.SuspendResponseConverter<HttpResponse, Any> {
    override suspend fun convert(result: KtorfitResult): Any =
        when (result) {
            is KtorfitResult.Failure -> {
                throw result.throwable
            }

            is KtorfitResult.Success -> {
                result.response.decodeOrEmpty()
            }
        }

    private suspend fun HttpResponse.decodeOrEmpty(): Any =
        try {
            body(typeData.typeInfo)
        } catch (cause: Exception) {
            if (cause is CancellationException) {
                throw cause
            }

            val responseText =
                try {
                    bodyAsText()
                } catch (readCause: CancellationException) {
                    throw readCause
                } catch (_: Exception) {
                    throw cause
                }

            if (responseText.isVvoNoDataHtml()) {
                emptyValueFactory()
            } else {
                throw cause
            }
        }
}

internal fun String.isVvoNoDataHtml(): Boolean =
    runCatching {
        val document = Ksoup.parse(this)
        document.title() == "微博-出错了" &&
            document.selectFirst("p.h5-4con")?.text()?.trim() == "暂无数据"
    }.getOrDefault(false)
