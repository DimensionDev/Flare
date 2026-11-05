// Copyright (c) Zefchain Labs, Inc.
// SPDX-License-Identifier: MIT OR Apache-2.0

package com.novi.serde

abstract class BinarySerializer(maxContainerDepth: Long) : Serializer {
    protected val output: SerdeByteArrayOutput = SerdeByteArrayOutput()
    private var containerDepthBudget: Long = maxContainerDepth

    @Throws(SerializationError::class)
    override fun increase_container_depth() {
        if (containerDepthBudget == 0L) {
            throw SerializationError("Exceeded maximum container depth")
        }
        containerDepthBudget -= 1
    }

    override fun decrease_container_depth() {
        containerDepthBudget += 1
    }

    @Throws(SerializationError::class)
    override fun serialize_str(value: String) {
        serialize_bytes(Bytes(value.encodeToByteArray()))
    }

    @Throws(SerializationError::class)
    override fun serialize_bytes(value: Bytes) {
        serialize_len(value.content.size.toLong())
        output.writeBytes(value.content, 0, value.content.size)
    }

    @Throws(SerializationError::class)
    override fun serialize_bool(value: Boolean) {
        output.writeByte(if (value) 1.toByte() else 0.toByte())
    }

    @Throws(SerializationError::class)
    override fun serialize_unit(value: Unit) {
        // Nothing to serialize.
    }

    @Throws(SerializationError::class)
    override fun serialize_char(value: Char) {
        throw SerializationError("Not implemented: serialize_char")
    }

    @Throws(SerializationError::class)
    override fun serialize_u8(value: UByte) {
        output.writeByte(value.toByte())
    }

    @Throws(SerializationError::class)
    override fun serialize_u16(value: UShort) {
        val v = value.toInt()
        output.writeByte((v and 0xff).toByte())
        output.writeByte(((v ushr 8) and 0xff).toByte())
    }

    @Throws(SerializationError::class)
    override fun serialize_u32(value: UInt) {
        val v = value.toInt()
        output.writeByte((v and 0xff).toByte())
        output.writeByte(((v ushr 8) and 0xff).toByte())
        output.writeByte(((v ushr 16) and 0xff).toByte())
        output.writeByte(((v ushr 24) and 0xff).toByte())
    }

    @Throws(SerializationError::class)
    override fun serialize_u64(value: ULong) {
        var v = value
        for (i in 0 until 8) {
            output.writeByte((v and 0xffuL).toByte())
            v = v shr 8
        }
    }

    @Throws(SerializationError::class)
    override fun serialize_u128(value: UInt128) {
        serialize_u64(value.low)
        serialize_u64(value.high)
    }

    @Throws(SerializationError::class)
    override fun serialize_i8(value: Byte) {
        serialize_u8(value.toUByte())
    }

    @Throws(SerializationError::class)
    override fun serialize_i16(value: Short) {
        serialize_u16(value.toUShort())
    }

    @Throws(SerializationError::class)
    override fun serialize_i32(value: Int) {
        serialize_u32(value.toUInt())
    }

    @Throws(SerializationError::class)
    override fun serialize_i64(value: Long) {
        serialize_u64(value.toULong())
    }

    @Throws(SerializationError::class)
    override fun serialize_i128(value: Int128) {
        serialize_u64(value.low)
        serialize_i64(value.high)
    }

    @Throws(SerializationError::class)
    override fun serialize_option_tag(value: Boolean) {
        output.writeByte(if (value) 1.toByte() else 0.toByte())
    }

    override fun get_buffer_offset(): Int {
        return output.size()
    }

    override fun get_bytes(): ByteArray {
        return output.toByteArray()
    }
}
