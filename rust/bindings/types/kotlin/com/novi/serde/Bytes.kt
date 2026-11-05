// Copyright (c) Zefchain Labs, Inc.
// SPDX-License-Identifier: MIT OR Apache-2.0

package com.novi.serde

/**
 * Inline value class wrapper around ByteArray.
 *
 * Provides proper value semantics for `equals` and `hashCode` using
 * structural equality (contentEquals/contentHashCode) instead of
 * referential equality.
 *
 * As a value class, this wrapper has zero runtime overhead - it's
 * inlined at compile time and compiles down to ByteArray at runtime.
 */
@JvmInline
value class Bytes(val content: ByteArray) {
    override fun toString(): String = content.contentToString()

    companion object {
        fun empty(): Bytes = Bytes(ByteArray(0))

        fun valueOf(content: ByteArray): Bytes = Bytes(content)
    }
}

/**
 * Extension function for structural equality comparison.
 * Required because value classes don't support overriding equals.
 */
fun Bytes.contentEquals(other: Bytes): Boolean =
    this.content.contentEquals(other.content)

/**
 * Extension function for structural hash code.
 * Required because value classes don't support overriding hashCode.
 */
fun Bytes.contentHashCode(): Int =
    content.contentHashCode()
