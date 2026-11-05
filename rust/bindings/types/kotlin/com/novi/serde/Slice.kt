// Copyright (c) Zefchain Labs, Inc.
// SPDX-License-Identifier: MIT OR Apache-2.0

package com.novi.serde

data class Slice(val start: Int, val end: Int) {
    companion object {
        fun compare_bytes(content: ByteArray, slice1: Slice, slice2: Slice): Int {
            val start1 = slice1.start
            val end1 = slice1.end
            val start2 = slice2.start
            val end2 = slice2.end
            var i = 0
            while (i < end1 - start1) {
                val index1 = start1 + i
                val index2 = start2 + i
                val byte1 = content[index1].toInt() and 0xff
                if (index2 >= end2) {
                    return 1
                }
                val byte2 = content[index2].toInt() and 0xff
                if (byte1 > byte2) {
                    return 1
                }
                if (byte1 < byte2) {
                    return -1
                }
                i += 1
            }
            if (end2 - start2 > end1 - start1) {
                return -1
            }
            return 0
        }
    }
}
