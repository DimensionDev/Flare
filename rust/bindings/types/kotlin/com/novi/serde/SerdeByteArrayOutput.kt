// Copyright (c) Zefchain Labs, Inc.
// SPDX-License-Identifier: MIT OR Apache-2.0

package com.novi.serde

class SerdeByteArrayOutput(initialCapacity: Int = 32) {
    private var buffer: ByteArray = ByteArray(initialCapacity)
    private var length: Int = 0

    fun writeByte(value: Byte) {
        ensureCapacity(1)
        buffer[length] = value
        length += 1
    }

    fun writeBytes(value: ByteArray, offset: Int, count: Int) {
        if (count == 0) {
            return
        }
        ensureCapacity(count)
        value.copyInto(buffer, destinationOffset = length, startIndex = offset, endIndex = offset + count)
        length += count
    }

    fun size(): Int {
        return length
    }

    fun toByteArray(): ByteArray {
        return buffer.copyOf(length)
    }

    fun getBuffer(): ByteArray {
        return buffer
    }

    private fun ensureCapacity(extra: Int) {
        val required = length + extra
        if (required <= buffer.size) {
            return
        }
        var newSize = buffer.size
        while (newSize < required) {
            newSize = newSize * 2
        }
        buffer = buffer.copyOf(newSize)
    }
}
