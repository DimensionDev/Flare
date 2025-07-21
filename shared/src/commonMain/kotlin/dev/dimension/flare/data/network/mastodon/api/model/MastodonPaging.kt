package dev.dimension.flare.data.network.mastodon.api.model

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.converter.Converter
import de.jensklingenberg.ktorfit.converter.KtorfitResult
import de.jensklingenberg.ktorfit.converter.TypeData
import dev.dimension.flare.common.decodeJson
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.util.reflect.serializer
import io.ktor.utils.io.InternalAPI
import kotlinx.serialization.builtins.ListSerializer

internal class MastodonPaging<T>(
    data: List<T>,
    val next: String? = null,
    val prev: String? = null,
) : List<T> by data {
    companion object {
        fun <T> from(response: Response<List<T>>): MastodonPaging<T> {
            val link = response.headers["link"]
            val next = link?.let { "max_id=(\\d+)".toRegex().find(it) }?.groupValues?.getOrNull(1)
            val prev = link?.let { "min_id=(\\d+)".toRegex().find(it) }?.groupValues?.getOrNull(1)
            return MastodonPaging(
                data = response.body() ?: emptyList(),
                next = next,
                prev = prev,
            )
        }
    }
}

internal class MastodonPagingConverterFactory : Converter.Factory {
    @OptIn( InternalAPI::class)
    override fun suspendResponseConverter(
        typeData: TypeData,
        ktorfit: Ktorfit,
    ): Converter.SuspendResponseConverter<HttpResponse, MastodonPaging<*>>? {
        if (typeData.typeInfo.type == MastodonPaging::class) {
            return object : Converter.SuspendResponseConverter<HttpResponse, MastodonPaging<*>> {
                override suspend fun convert(result: KtorfitResult): MastodonPaging<*> =
                    when (result) {
                        is KtorfitResult.Failure -> {
                            throw result.throwable
                        }

                        is KtorfitResult.Success -> {
                            val link = result.response.headers["link"]
                            val next = link?.let { "max_id=(\\d+)".toRegex().find(it) }?.groupValues?.getOrNull(1)
                            val prev = link?.let { "min_id=(\\d+)".toRegex().find(it) }?.groupValues?.getOrNull(1)
                            val body =
                                result.response.bodyAsText().decodeJson(
                                    ListSerializer(
                                        typeData.typeArgs
                                            .first()
                                            .typeInfo.serializer(),
                                    ),
                                )
                            MastodonPaging(
                                data = body,
                                next = next,
                                prev = prev,
                            )
                        }
                    }
            }
        }
        return null
    }
}
