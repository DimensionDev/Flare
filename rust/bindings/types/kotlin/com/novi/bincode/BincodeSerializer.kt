// Copyright (c) Zefchain Labs, Inc.
// SPDX-License-Identifier: MIT OR Apache-2.0

package com.novi.bincode

import com.novi.serde.BinarySerializer
import com.novi.serde.SerializationError

class BincodeSerializer : BinarySerializer(Long.MAX_VALUE) {
    @Throws(SerializationError::class)
    override fun serialize_f32(value: Float) {
        serialize_i32(value.toRawBits())
    }

    @Throws(SerializationError::class)
    override fun serialize_f64(value: Double) {
        serialize_i64(value.toRawBits())
    }

    @Throws(SerializationError::class)
    override fun serialize_len(value: Long) {
        serialize_u64(value.toULong())
    }

    @Throws(SerializationError::class)
    override fun serialize_variant_index(value: Int) {
        serialize_u32(value.toUInt())
    }

    override fun sort_map_entries(offsets: IntArray) {
        // Not required by the format.
    }
}
