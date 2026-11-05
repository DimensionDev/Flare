// Copyright (c) Zefchain Labs, Inc.
// SPDX-License-Identifier: MIT OR Apache-2.0

package com.novi.serde

interface Deserializer {
    @Throws(DeserializationError::class)
    fun deserialize_str(): String

    @Throws(DeserializationError::class)
    fun deserialize_bytes(): Bytes

    @Throws(DeserializationError::class)
    fun deserialize_bool(): Boolean

    @Throws(DeserializationError::class)
    fun deserialize_unit(): Unit

    @Throws(DeserializationError::class)
    fun deserialize_char(): Char

    @Throws(DeserializationError::class)
    fun deserialize_f32(): Float

    @Throws(DeserializationError::class)
    fun deserialize_f64(): Double

    @Throws(DeserializationError::class)
    fun deserialize_u8(): UByte

    @Throws(DeserializationError::class)
    fun deserialize_u16(): UShort

    @Throws(DeserializationError::class)
    fun deserialize_u32(): UInt

    @Throws(DeserializationError::class)
    fun deserialize_u64(): ULong

    @Throws(DeserializationError::class)
    fun deserialize_u128(): UInt128

    @Throws(DeserializationError::class)
    fun deserialize_i8(): Byte

    @Throws(DeserializationError::class)
    fun deserialize_i16(): Short

    @Throws(DeserializationError::class)
    fun deserialize_i32(): Int

    @Throws(DeserializationError::class)
    fun deserialize_i64(): Long

    @Throws(DeserializationError::class)
    fun deserialize_i128(): Int128

    @Throws(DeserializationError::class)
    fun deserialize_len(): Long

    @Throws(DeserializationError::class)
    fun deserialize_variant_index(): Int

    @Throws(DeserializationError::class)
    fun deserialize_option_tag(): Boolean

    @Throws(DeserializationError::class)
    fun increase_container_depth()

    fun decrease_container_depth()

    fun get_buffer_offset(): Int

    @Throws(DeserializationError::class)
    fun check_that_key_slices_are_increasing(key1: Slice, key2: Slice)
}
