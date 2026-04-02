package dev.dimension.flare.data.database.adapter

import dev.dimension.flare.data.database.app.model.SubscriptionType

internal class SubscriptionTypeConverter {
    @androidx.room3.TypeConverter
    fun fromString(value: String): SubscriptionType = SubscriptionType.valueOf(value)

    @androidx.room3.TypeConverter
    fun fromEnum(value: SubscriptionType): String = value.name
}
