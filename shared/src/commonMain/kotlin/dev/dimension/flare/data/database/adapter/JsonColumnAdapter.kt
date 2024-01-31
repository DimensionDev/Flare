package dev.dimension.flare.data.database.adapter

import app.cash.sqldelight.ColumnAdapter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import kotlinx.serialization.KSerializer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal class JsonColumnAdapter<T : Any>(
    private val serializer: KSerializer<T>,
) : ColumnAdapter<T, String> {
    override fun decode(databaseValue: String): T = Base64.decode(databaseValue).decodeToString().decodeJson(serializer)

    override fun encode(value: T) = Base64.encode(value.encodeJson(serializer).encodeToByteArray())
}
