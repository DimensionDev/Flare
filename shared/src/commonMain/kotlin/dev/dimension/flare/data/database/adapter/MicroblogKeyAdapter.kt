package dev.dimension.flare.data.database.adapter

import app.cash.sqldelight.ColumnAdapter
import dev.dimension.flare.model.MicroBlogKey

internal class MicroblogKeyAdapter : ColumnAdapter<MicroBlogKey, String> {
    override fun decode(databaseValue: String): MicroBlogKey = MicroBlogKey.valueOf(databaseValue)

    override fun encode(value: MicroBlogKey): String = value.toString()
}
