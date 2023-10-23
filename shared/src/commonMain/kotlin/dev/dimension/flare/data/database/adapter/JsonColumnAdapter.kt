package dev.dimension.flare.data.database.adapter

import app.cash.sqldelight.ColumnAdapter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import kotlinx.serialization.KSerializer

internal class JsonColumnAdapter<T : Any>(
    private val serializer: KSerializer<T>,
) : ColumnAdapter<T, String> {
    override fun decode(databaseValue: String): T = databaseValue.decodeJson(serializer)

    override fun encode(value: T) = value.encodeJson(serializer)
}
