package dev.dimension.flare.data.network.mastodon.api.model

import de.jensklingenberg.ktorfit.Ktorfit
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
    private val data: List<T>,
    val next: String? = null,
    val prev: String? = null,
) : List<T> by data {
    operator fun plus(other: List<T>): MastodonPaging<T> =
        MastodonPaging(
            data = this.data.plus(other),
            next = this.next,
            prev = this.prev,
        )
}

internal class MastodonPagingConverterFactory : Converter.Factory {
    @OptIn(InternalAPI::class)
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
                            val body =
                                result.response.bodyAsText().decodeJson(
                                    ListSerializer(
                                        typeData.typeArgs
                                            .first()
                                            .typeInfo
                                            .serializer(),
                                    ),
                                )
                            val link = result.response.headers["link"]
                            if (result.response.headers.contains("link") && link != null) {
                                val next =
                                    "max_id=(\\d+)"
                                        .toRegex()
                                        .find(link)
                                        ?.groupValues
                                        ?.getOrNull(1)
                                val prev =
                                    "min_id=(\\d+)"
                                        .toRegex()
                                        .find(link)
                                        ?.groupValues
                                        ?.getOrNull(1)
                                MastodonPaging(
                                    data = body,
                                    next = next,
                                    prev = prev,
                                )
                            } else {
                                val next =
                                    when (val last = body.lastOrNull()) {
                                        is Status -> last.id
                                        is Notification -> last.id
                                        is Account -> last.id
                                        is MastodonList -> last.id
                                        else -> null
                                    }
                                MastodonPaging(
                                    data = body,
                                    next = next,
                                )
                            }
                        }
                    }
            }
        }
        return null
    }
}
