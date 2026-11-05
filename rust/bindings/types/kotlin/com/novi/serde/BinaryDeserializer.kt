// Copyright (c) Zefchain Labs, Inc.
// SPDX-License-Identifier: MIT OR Apache-2.0

package com.novi.serde

abstract class BinaryDeserializer(
    protected val input: ByteArray,
    maxContainerDepth: Long
) : Deserializer {
    private var position: Int = 0
    private var containerDepthBudget: Long = maxContainerDepth

    @Throws(DeserializationError::class)
    override fun increase_container_depth() {
        if (containerDepthBudget == 0L) {
            throw DeserializationError("Exceeded maximum container depth")
        }
        containerDepthBudget -= 1
    }

    override fun decrease_container_depth() {
        containerDepthBudget += 1
    }

    @Throws(DeserializationError::class)
    override fun deserialize_str(): String {
        val len = deserialize_len()
        if (len < 0 || len > Int.MAX_VALUE.toLong()) {
            throw DeserializationError("Incorrect length value for Kotlin string")
        }
        val content = readBytes(len.toInt())
        return try {
            content.decodeToString(throwOnInvalidSequence = true)
        } catch (e: Throwable) {
            throw DeserializationError("Incorrect UTF8 string")
        }
    }

    @Throws(DeserializationError::class)
    override fun deserialize_bytes(): Bytes {
        val len = deserialize_len()
        if (len < 0 || len > Int.MAX_VALUE.toLong()) {
            throw DeserializationError("Incorrect length value for Kotlin array")
        }
        return Bytes(readBytes(len.toInt()))
    }

    @Throws(DeserializationError::class)
    override fun deserialize_bool(): Boolean {
        val value = getByte()
        return when (value.toInt()) {
            0 -> false
            1 -> true
            else -> throw DeserializationError("Incorrect boolean value")
        }
    }

    @Throws(DeserializationError::class)
    override fun deserialize_unit(): Unit {
        return Unit
    }

    @Throws(DeserializationError::class)
    override fun deserialize_char(): Char {
        throw DeserializationError("Not implemented: deserialize_char")
    }

    @Throws(DeserializationError::class)
    override fun deserialize_u8(): UByte {
        return getByte().toUByte()
    }

    @Throws(DeserializationError::class)
    override fun deserialize_u16(): UShort {
        val b0 = getByte().toInt() and 0xff
        val b1 = getByte().toInt() and 0xff
        return (b0 or (b1 shl 8)).toUShort()
    }

    @Throws(DeserializationError::class)
    override fun deserialize_u32(): UInt {
        val b0 = getByte().toInt() and 0xff
        val b1 = getByte().toInt() and 0xff
        val b2 = getByte().toInt() and 0xff
        val b3 = getByte().toInt() and 0xff
        return (b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)).toUInt()
    }

    @Throws(DeserializationError::class)
    override fun deserialize_u64(): ULong {
        var value = 0uL
        for (shift in 0 until 64 step 8) {
            val byteValue = getByte().toULong() and 0xffuL
            value = value or (byteValue shl shift)
        }
        return value
    }

    @Throws(DeserializationError::class)
    override fun deserialize_u128(): UInt128 {
        val low = deserialize_u64()
        val high = deserialize_u64()
        return UInt128(high = high, low = low)
    }

    @Throws(DeserializationError::class)
    override fun deserialize_i8(): Byte {
        return getByte()
    }

    @Throws(DeserializationError::class)
    override fun deserialize_i16(): Short {
        return deserialize_u16().toShort()
    }

    @Throws(DeserializationError::class)
    override fun deserialize_i32(): Int {
        return deserialize_u32().toInt()
    }

    @Throws(DeserializationError::class)
    override fun deserialize_i64(): Long {
        return deserialize_u64().toLong()
    }

    @Throws(DeserializationError::class)
    override fun deserialize_i128(): Int128 {
        val low = deserialize_u64()
        val high = deserialize_i64()
        return Int128(high = high, low = low)
    }

    @Throws(DeserializationError::class)
    override fun deserialize_option_tag(): Boolean {
        return deserialize_bool()
    }

    override fun get_buffer_offset(): Int {
        return position
    }

    protected fun getInt(): Int {
        val b0 = getByte().toInt() and 0xff
        val b1 = getByte().toInt() and 0xff
        val b2 = getByte().toInt() and 0xff
        val b3 = getByte().toInt() and 0xff
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    protected fun getLong(): Long {
        var value = 0L
        for (shift in 0 until 64 step 8) {
            val byteValue = getByte().toLong() and 0xffL
            value = value or (byteValue shl shift)
        }
        return value
    }

    protected fun getByte(): Byte {
        requireAvailable(1)
        val value = input[position]
        position += 1
        return value
    }

    private fun readBytes(count: Int): ByteArray {
        requireAvailable(count)
        val slice = input.copyOfRange(position, position + count)
        position += count
        return slice
    }

    private fun requireAvailable(count: Int) {
        if (position + count > input.size) {
            throw DeserializationError(INPUT_NOT_LARGE_ENOUGH)
        }
    }

    companion object {
        private const val INPUT_NOT_LARGE_ENOUGH = "Input is not large enough"
    }
}
