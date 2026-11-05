package dev.dimension.flare.counter

import com.novi.bincode.BincodeDeserializer
import com.novi.bincode.BincodeSerializer
import com.novi.serde.DeserializationError
import com.novi.serde.Deserializer
import com.novi.serde.Serializer

fun <T> List<T>.serialize(
    serializer: Serializer,
    serializeElement: Serializer.(T) -> Unit,
) {
    serializer.serialize_len(size.toLong())
    forEach { element ->
        serializer.serializeElement(element)
    }
}

fun <T> Deserializer.deserializeListOf(deserializeElement: (Deserializer) -> T): List<T> {
    val length = deserialize_len()
    val list = mutableListOf<T>()
    repeat(length.toInt()) {
        list.add(deserializeElement(this))
    }
    return list
}

fun <T> T?.serializeOptionOf(
    serializer: Serializer,
    serializeElement: Serializer.(T) -> Unit,
) {
    if (this != null) {
        serializer.serialize_option_tag(true)
        serializer.serializeElement(this)
    } else {
        serializer.serialize_option_tag(false)
    }
}

fun <T> Deserializer.deserializeOptionOf(deserializeElement: (Deserializer) -> T): T? {
    val tag = deserialize_option_tag()
    return if (tag) {
        deserializeElement(this)
    } else {
        null
    }
}

sealed interface CounterEffect {
    fun serialize(serializer: Serializer)

    fun bincodeSerialize(): ByteArray {
        val serializer = BincodeSerializer()
        serialize(serializer)
        return serializer.get_bytes()
    }

    data class Render(
        val value: dev.dimension.flare.counter.RenderOperation,
    ) : CounterEffect {
        override fun serialize(serializer: Serializer) {
            serializer.increase_container_depth()
            serializer.serialize_variant_index(0)
            value.serialize(serializer)
            serializer.decrease_container_depth()
        }

        companion object {
            fun deserialize(deserializer: Deserializer): Render {
                deserializer.increase_container_depth()
                val value = dev.dimension.flare.counter.RenderOperation.deserialize(deserializer)
                deserializer.decrease_container_depth()
                return Render(value)
            }
        }
    }

    companion object {
        @Throws(DeserializationError::class)
        fun deserialize(deserializer: Deserializer): CounterEffect {
            val index = deserializer.deserialize_variant_index()
            return when (index) {
                0 -> Render.deserialize(deserializer)
                else -> throw DeserializationError("Unknown variant index for CounterEffect: $index")
            }
        }

        @Throws(DeserializationError::class)
        fun bincodeDeserialize(input: ByteArray?): CounterEffect {
            if (input == null) {
                throw DeserializationError("Cannot deserialize null array")
            }
            val deserializer = BincodeDeserializer(input)
            val value = deserialize(deserializer)
            if (deserializer.get_buffer_offset() < input.size) {
                throw DeserializationError("Some input bytes were not read")
            }
            return value
        }
    }
}

enum class CounterEvent {
    INCREMENT,
    DECREMENT,
    RESET;

    fun serialize(serializer: Serializer) {
        serializer.increase_container_depth()
        serializer.serialize_variant_index(ordinal)
        serializer.decrease_container_depth()
    }

    fun bincodeSerialize(): ByteArray {
        val serializer = BincodeSerializer()
        serialize(serializer)
        return serializer.get_bytes()
    }

    companion object {
        @Throws(DeserializationError::class)
        fun deserialize(deserializer: Deserializer): CounterEvent {
            deserializer.increase_container_depth()
            val index = deserializer.deserialize_variant_index()
            deserializer.decrease_container_depth()
            return when (index) {
                0 -> INCREMENT
                1 -> DECREMENT
                2 -> RESET
                else -> throw DeserializationError("Unknown variant index for CounterEvent: $index")
            }
        }

        @Throws(DeserializationError::class)
        fun bincodeDeserialize(input: ByteArray?): CounterEvent {
            if (input == null) {
                throw DeserializationError("Cannot deserialize null array")
            }
            val deserializer = BincodeDeserializer(input)
            val value = deserialize(deserializer)
            if (deserializer.get_buffer_offset() < input.size) {
                throw DeserializationError("Some input bytes were not read")
            }
            return value
        }
    }
}

data class CounterState(
    val value: Long,
    val revision: ULong,
    val error: dev.dimension.flare.counter.UiError? = null,
) {
    fun serialize(serializer: Serializer) {
        serializer.increase_container_depth()
        serializer.serialize_i64(value)
        serializer.serialize_u64(revision)
        error.serializeOptionOf(serializer) {
            it.serialize(serializer)
        }
        serializer.decrease_container_depth()
    }

    fun bincodeSerialize(): ByteArray {
        val serializer = BincodeSerializer()
        serialize(serializer)
        return serializer.get_bytes()
    }

    companion object {
        fun deserialize(deserializer: Deserializer): CounterState {
            deserializer.increase_container_depth()
            val value = deserializer.deserialize_i64()
            val revision = deserializer.deserialize_u64()
            val error =
                deserializer.deserializeOptionOf {
                    dev.dimension.flare.counter.UiError.deserialize(deserializer)
                }
            deserializer.decrease_container_depth()
            return CounterState(value, revision, error)
        }

        @Throws(DeserializationError::class)
        fun bincodeDeserialize(input: ByteArray?): CounterState {
            if (input == null) {
                throw DeserializationError("Cannot deserialize null array")
            }
            val deserializer = BincodeDeserializer(input)
            val value = deserialize(deserializer)
            if (deserializer.get_buffer_offset() < input.size) {
                throw DeserializationError("Some input bytes were not read")
            }
            return value
        }
    }
}

/// The single operation `Render` implements.
data object RenderOperation {
    fun serialize(serializer: Serializer) {}

    fun bincodeSerialize(): ByteArray {
        val serializer = BincodeSerializer()
        serialize(serializer)
        return serializer.get_bytes()
    }

    fun deserialize(deserializer: Deserializer): RenderOperation {
        return RenderOperation
    }

    @Throws(DeserializationError::class)
    fun bincodeDeserialize(input: ByteArray?): RenderOperation {
        if (input == null) {
            throw DeserializationError("Cannot deserialize null array")
        }
        val deserializer = BincodeDeserializer(input)
        val value = deserialize(deserializer)
        if (deserializer.get_buffer_offset() < input.size) {
            throw DeserializationError("Some input bytes were not read")
        }
        return value
    }
}

/// Request for a side-effect passed from the Core to the Shell.
/// 
/// The `EffectId` links the `Request` with the corresponding call to [`Core::resolve`] to pass the data back
/// to the [`App::update`] function (wrapped in the event provided to the capability originating the effect).
data class Request(
    val id: UInt,
    val effect: dev.dimension.flare.counter.CounterEffect,
) {
    fun serialize(serializer: Serializer) {
        serializer.increase_container_depth()
        serializer.serialize_u32(id)
        effect.serialize(serializer)
        serializer.decrease_container_depth()
    }

    fun bincodeSerialize(): ByteArray {
        val serializer = BincodeSerializer()
        serialize(serializer)
        return serializer.get_bytes()
    }

    companion object {
        fun deserialize(deserializer: Deserializer): Request {
            deserializer.increase_container_depth()
            val id = deserializer.deserialize_u32()
            val effect = dev.dimension.flare.counter.CounterEffect.deserialize(deserializer)
            deserializer.decrease_container_depth()
            return Request(id, effect)
        }

        @Throws(DeserializationError::class)
        fun bincodeDeserialize(input: ByteArray?): Request {
            if (input == null) {
                throw DeserializationError("Cannot deserialize null array")
            }
            val deserializer = BincodeDeserializer(input)
            val value = deserialize(deserializer)
            if (deserializer.get_buffer_offset() < input.size) {
                throw DeserializationError("Some input bytes were not read")
            }
            return value
        }
    }
}

/// A batch of effect requests from the Core to the Shell, as serialised by
/// [`Bridge::update`] and [`Bridge::resolve`].
/// 
/// The wire format is identical to `Vec<Request<Eff>>` (the newtype is
/// `serde(transparent)`), so existing shell code that already deserialises
/// a `Vec<Request>` remains binary-compatible.
/// 
/// Registering this type with the type-generation system causes the code
/// generators to emit a `Requests` type (with a `value` field containing the
/// list) together with a top-level `bincodeDeserialize` / `BincodeDeserialize`
/// helper, replacing the hand-written extension files that were previously
/// appended by `add_extensions()`.
data class Requests(
    val value: List<dev.dimension.flare.counter.Request>,
) {
    fun serialize(serializer: Serializer) {
        serializer.increase_container_depth()
        value.serialize(serializer) {
            it.serialize(serializer)
        }
        serializer.decrease_container_depth()
    }

    fun bincodeSerialize(): ByteArray {
        val serializer = BincodeSerializer()
        serialize(serializer)
        return serializer.get_bytes()
    }

    companion object {
        fun deserialize(deserializer: Deserializer): Requests {
            deserializer.increase_container_depth()
            val value =
                deserializer.deserializeListOf {
                    dev.dimension.flare.counter.Request.deserialize(deserializer)
                }
            deserializer.decrease_container_depth()
            return Requests(value)
        }

        @Throws(DeserializationError::class)
        fun bincodeDeserialize(input: ByteArray?): Requests {
            if (input == null) {
                throw DeserializationError("Cannot deserialize null array")
            }
            val deserializer = BincodeDeserializer(input)
            val value = deserialize(deserializer)
            if (deserializer.get_buffer_offset() < input.size) {
                throw DeserializationError("Some input bytes were not read")
            }
            return value
        }
    }
}

/// An owned presentation error with a stable code and safe fallback text.
data class UiError(
    val code: String,
    val message: String,
) {
    fun serialize(serializer: Serializer) {
        serializer.increase_container_depth()
        serializer.serialize_str(code)
        serializer.serialize_str(message)
        serializer.decrease_container_depth()
    }

    fun bincodeSerialize(): ByteArray {
        val serializer = BincodeSerializer()
        serialize(serializer)
        return serializer.get_bytes()
    }

    companion object {
        fun deserialize(deserializer: Deserializer): UiError {
            deserializer.increase_container_depth()
            val code = deserializer.deserialize_str()
            val message = deserializer.deserialize_str()
            deserializer.decrease_container_depth()
            return UiError(code, message)
        }

        @Throws(DeserializationError::class)
        fun bincodeDeserialize(input: ByteArray?): UiError {
            if (input == null) {
                throw DeserializationError("Cannot deserialize null array")
            }
            val deserializer = BincodeDeserializer(input)
            val value = deserialize(deserializer)
            if (deserializer.get_buffer_offset() < input.size) {
                throw DeserializationError("Some input bytes were not read")
            }
            return value
        }
    }
}
