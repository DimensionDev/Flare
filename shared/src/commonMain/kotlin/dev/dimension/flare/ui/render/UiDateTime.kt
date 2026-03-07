package dev.dimension.flare.ui.render

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.humanizer.Formatter.absolute
import dev.dimension.flare.ui.humanizer.Formatter.full
import dev.dimension.flare.ui.humanizer.Formatter.relative
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

public expect class PlatformDateTime

internal expect fun Instant.toPlatform(): PlatformDateTime

@Serializable(with = UiDateTimeSerializer::class)
@Immutable
public data class UiDateTime internal constructor(
    val value: Instant,
) {
    val platformValue: PlatformDateTime by lazy {
        value.toPlatform()
    }
    val relative: String = value.relative()
    val full: String = value.full()
    val absolute: String = value.absolute()

    val shouldShowFull: Boolean by lazy {
        val compareTo = Clock.System.now()
        val diff = compareTo - this.value
        diff.inWholeDays >= 7
    }
}

public fun Instant.toUi(): UiDateTime = UiDateTime(this)

internal operator fun UiDateTime.compareTo(other: UiDateTime): Int = value.compareTo(other.value)

internal object UiDateTimeSerializer : kotlinx.serialization.KSerializer<UiDateTime> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor
        get() =
            kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
                "UiDateTime",
                kotlinx.serialization.descriptors.PrimitiveKind.STRING,
            )

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): UiDateTime {
        val epochMillis = decoder.decodeLong()
        return UiDateTime(Instant.fromEpochMilliseconds(epochMillis))
    }

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: UiDateTime,
    ) {
        encoder.encodeLong(value.value.toEpochMilliseconds())
    }
}
