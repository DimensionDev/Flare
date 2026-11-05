// Copyright (c) Zefchain Labs, Inc.
// SPDX-License-Identifier: MIT OR Apache-2.0

package com.novi.bincode

import com.novi.serde.BinaryDeserializer
import com.novi.serde.DeserializationError
import com.novi.serde.Slice

class BincodeDeserializer(input: ByteArray) : BinaryDeserializer(input, Long.MAX_VALUE) {
    @Throws(DeserializationError::class)
    override fun deserialize_f32(): Float {
        return Float.fromBits(getInt())
    }

    @Throws(DeserializationError::class)
    override fun deserialize_f64(): Double {
        return Double.fromBits(getLong())
    }

    @Throws(DeserializationError::class)
    override fun deserialize_len(): Long {
        val value = getLong()
        if (value < 0 || value > Int.MAX_VALUE.toLong()) {
            throw DeserializationError("Incorrect length value")
        }
        return value
    }

    @Throws(DeserializationError::class)
    override fun deserialize_variant_index(): Int {
        return getInt()
    }

    @Throws(DeserializationError::class)
    override fun check_that_key_slices_are_increasing(key1: Slice, key2: Slice) {
        // Not required by the format.
    }
}
