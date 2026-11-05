// Copyright (c) Zefchain Labs, Inc.
// SPDX-License-Identifier: MIT OR Apache-2.0

package com.novi.serde

interface Serializer {
    @Throws(SerializationError::class)
    fun serialize_str(value: String)

    @Throws(SerializationError::class)
    fun serialize_bytes(value: Bytes)

    @Throws(SerializationError::class)
    fun serialize_bool(value: Boolean)

    @Throws(SerializationError::class)
    fun serialize_unit(value: Unit)

    @Throws(SerializationError::class)
    fun serialize_char(value: Char)

    @Throws(SerializationError::class)
    fun serialize_f32(value: Float)

    @Throws(SerializationError::class)
    fun serialize_f64(value: Double)

    @Throws(SerializationError::class)
    fun serialize_u8(value: UByte)

    @Throws(SerializationError::class)
    fun serialize_u16(value: UShort)

    @Throws(SerializationError::class)
    fun serialize_u32(value: UInt)

    @Throws(SerializationError::class)
    fun serialize_u64(value: ULong)

    @Throws(SerializationError::class)
    fun serialize_u128(value: UInt128)

    @Throws(SerializationError::class)
    fun serialize_i8(value: Byte)

    @Throws(SerializationError::class)
    fun serialize_i16(value: Short)

    @Throws(SerializationError::class)
    fun serialize_i32(value: Int)

    @Throws(SerializationError::class)
    fun serialize_i64(value: Long)

    @Throws(SerializationError::class)
    fun serialize_i128(value: Int128)

    @Throws(SerializationError::class)
    fun serialize_len(value: Long)

    @Throws(SerializationError::class)
    fun serialize_variant_index(value: Int)

    @Throws(SerializationError::class)
    fun serialize_option_tag(value: Boolean)

    @Throws(SerializationError::class)
    fun increase_container_depth()

    fun decrease_container_depth()

    fun get_buffer_offset(): Int

    fun sort_map_entries(offsets: IntArray)

    fun get_bytes(): ByteArray
}
