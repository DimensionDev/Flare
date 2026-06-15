package dev.dimension.flare.data.network.xqt.xchat

import kotlin.experimental.xor

internal object XChatSecretBox {
    fun encryptBody(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        require(key.size == KEY_SIZE) { "secretbox key must be $KEY_SIZE bytes" }
        require(nonce.size == NONCE_SIZE) { "secretbox nonce must be $NONCE_SIZE bytes" }
        val box = secretBox(plaintext, nonce, key)
        return nonce + box
    }

    fun decryptBody(
        frame: ByteArray,
        key: ByteArray,
    ): ByteArray? {
        require(key.size == KEY_SIZE) { "secretbox key must be $KEY_SIZE bytes" }
        if (frame.size < NONCE_SIZE + TAG_SIZE) return null
        val nonce = frame.copyOfRange(0, NONCE_SIZE)
        val box = frame.copyOfRange(NONCE_SIZE, frame.size)
        return secretBoxOpen(box, nonce, key)
    }

    private fun secretBox(
        message: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val stream = xsalsa20Stream(message.size + ZERO_PREFIX_SIZE, nonce, key)
        val cipher = ByteArray(message.size)
        for (index in message.indices) {
            cipher[index] = message[index] xor stream[index + ZERO_PREFIX_SIZE]
        }
        val tag = poly1305(cipher, stream.copyOfRange(0, AUTH_KEY_SIZE))
        return tag + cipher
    }

    private fun secretBoxOpen(
        box: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray? {
        if (box.size < TAG_SIZE) return null
        val tag = box.copyOfRange(0, TAG_SIZE)
        val cipher = box.copyOfRange(TAG_SIZE, box.size)
        val stream = xsalsa20Stream(cipher.size + ZERO_PREFIX_SIZE, nonce, key)
        val expected = poly1305(cipher, stream.copyOfRange(0, AUTH_KEY_SIZE))
        if (!constantTimeEquals(tag, expected)) return null
        return ByteArray(cipher.size) { index ->
            cipher[index] xor stream[index + ZERO_PREFIX_SIZE]
        }
    }

    private fun xsalsa20Stream(
        length: Int,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val subKey = hSalsa20(nonce.copyOfRange(0, 16), key)
        return salsa20Stream(length, nonce.copyOfRange(16, 24), subKey)
    }

    private fun salsa20Stream(
        length: Int,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val output = ByteArray(length)
        var counter = 0L
        var offset = 0
        while (offset < length) {
            val block = salsa20Block(nonce, key, counter)
            val count = minOf(block.size, length - offset)
            block.copyInto(output, offset, endIndex = count)
            offset += count
            counter++
        }
        return output
    }

    private fun salsa20Block(
        nonce: ByteArray,
        key: ByteArray,
        counter: Long,
    ): ByteArray {
        val state =
            intArrayOf(
                load32(SIGMA, 0),
                load32(key, 0),
                load32(key, 4),
                load32(key, 8),
                load32(key, 12),
                load32(SIGMA, 4),
                load32(nonce, 0),
                load32(nonce, 4),
                counter.toInt(),
                (counter ushr 32).toInt(),
                load32(SIGMA, 8),
                load32(key, 16),
                load32(key, 20),
                load32(key, 24),
                load32(key, 28),
                load32(SIGMA, 12),
            )
        val working = salsaRounds(state)
        val output = ByteArray(64)
        for (index in 0 until 16) {
            store32(output, index * 4, working[index] + state[index])
        }
        return output
    }

    private fun hSalsa20(
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val state =
            intArrayOf(
                load32(SIGMA, 0),
                load32(key, 0),
                load32(key, 4),
                load32(key, 8),
                load32(key, 12),
                load32(SIGMA, 4),
                load32(nonce, 0),
                load32(nonce, 4),
                load32(nonce, 8),
                load32(nonce, 12),
                load32(SIGMA, 8),
                load32(key, 16),
                load32(key, 20),
                load32(key, 24),
                load32(key, 28),
                load32(SIGMA, 12),
            )
        val working = salsaRounds(state)
        val output = ByteArray(KEY_SIZE)
        intArrayOf(0, 5, 10, 15, 6, 7, 8, 9).forEachIndexed { index, stateIndex ->
            store32(output, index * 4, working[stateIndex])
        }
        return output
    }

    private fun salsaRounds(input: IntArray): IntArray {
        val x = input.copyOf()
        repeat(10) {
            x[4] = x[4] xor rotateLeft(x[0] + x[12], 7)
            x[8] = x[8] xor rotateLeft(x[4] + x[0], 9)
            x[12] = x[12] xor rotateLeft(x[8] + x[4], 13)
            x[0] = x[0] xor rotateLeft(x[12] + x[8], 18)
            x[9] = x[9] xor rotateLeft(x[5] + x[1], 7)
            x[13] = x[13] xor rotateLeft(x[9] + x[5], 9)
            x[1] = x[1] xor rotateLeft(x[13] + x[9], 13)
            x[5] = x[5] xor rotateLeft(x[1] + x[13], 18)
            x[14] = x[14] xor rotateLeft(x[10] + x[6], 7)
            x[2] = x[2] xor rotateLeft(x[14] + x[10], 9)
            x[6] = x[6] xor rotateLeft(x[2] + x[14], 13)
            x[10] = x[10] xor rotateLeft(x[6] + x[2], 18)
            x[3] = x[3] xor rotateLeft(x[15] + x[11], 7)
            x[7] = x[7] xor rotateLeft(x[3] + x[15], 9)
            x[11] = x[11] xor rotateLeft(x[7] + x[3], 13)
            x[15] = x[15] xor rotateLeft(x[11] + x[7], 18)
            x[1] = x[1] xor rotateLeft(x[0] + x[3], 7)
            x[2] = x[2] xor rotateLeft(x[1] + x[0], 9)
            x[3] = x[3] xor rotateLeft(x[2] + x[1], 13)
            x[0] = x[0] xor rotateLeft(x[3] + x[2], 18)
            x[6] = x[6] xor rotateLeft(x[5] + x[4], 7)
            x[7] = x[7] xor rotateLeft(x[6] + x[5], 9)
            x[4] = x[4] xor rotateLeft(x[7] + x[6], 13)
            x[5] = x[5] xor rotateLeft(x[4] + x[7], 18)
            x[11] = x[11] xor rotateLeft(x[10] + x[9], 7)
            x[8] = x[8] xor rotateLeft(x[11] + x[10], 9)
            x[9] = x[9] xor rotateLeft(x[8] + x[11], 13)
            x[10] = x[10] xor rotateLeft(x[9] + x[8], 18)
            x[12] = x[12] xor rotateLeft(x[15] + x[14], 7)
            x[13] = x[13] xor rotateLeft(x[12] + x[15], 9)
            x[14] = x[14] xor rotateLeft(x[13] + x[12], 13)
            x[15] = x[15] xor rotateLeft(x[14] + x[13], 18)
        }
        return x
    }

    private fun poly1305(
        message: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val r0 = load32Long(key, 0) and MASK_26
        val r1 = (load32Long(key, 3) ushr 2) and 0x3ffff03L
        val r2 = (load32Long(key, 6) ushr 4) and 0x3ffc0ffL
        val r3 = (load32Long(key, 9) ushr 6) and 0x3f03fffL
        val r4 = (load32Long(key, 12) ushr 8) and 0x00fffffL
        val s1 = r1 * 5
        val s2 = r2 * 5
        val s3 = r3 * 5
        val s4 = r4 * 5
        var h0 = 0L
        var h1 = 0L
        var h2 = 0L
        var h3 = 0L
        var h4 = 0L
        var offset = 0
        while (offset < message.size) {
            val block = ByteArray(16)
            val blockSize = minOf(16, message.size - offset)
            message.copyInto(block, endIndex = offset + blockSize, startIndex = offset)
            val hibit =
                if (blockSize == 16) {
                    1L shl 24
                } else {
                    block[blockSize] = 1
                    0L
                }
            val t0 = load32Long(block, 0)
            val t1 = load32Long(block, 4)
            val t2 = load32Long(block, 8)
            val t3 = load32Long(block, 12)
            h0 += t0 and MASK_26
            h1 += ((t1 shl 6) or (t0 ushr 26)) and MASK_26
            h2 += ((t2 shl 12) or (t1 ushr 20)) and MASK_26
            h3 += ((t3 shl 18) or (t2 ushr 14)) and MASK_26
            h4 += (t3 ushr 8) or hibit

            val d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1
            val d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2
            val d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3
            val d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4
            val d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0

            var carry = d0 ushr 26
            h0 = d0 and MASK_26
            h1 = d1 + carry
            carry = h1 ushr 26
            h1 = h1 and MASK_26
            h2 = d2 + carry
            carry = h2 ushr 26
            h2 = h2 and MASK_26
            h3 = d3 + carry
            carry = h3 ushr 26
            h3 = h3 and MASK_26
            h4 = d4 + carry
            carry = h4 ushr 26
            h4 = h4 and MASK_26
            h0 += carry * 5
            carry = h0 ushr 26
            h0 = h0 and MASK_26
            h1 += carry

            offset += blockSize
        }

        var carry = h1 ushr 26
        h1 = h1 and MASK_26
        h2 += carry
        carry = h2 ushr 26
        h2 = h2 and MASK_26
        h3 += carry
        carry = h3 ushr 26
        h3 = h3 and MASK_26
        h4 += carry
        carry = h4 ushr 26
        h4 = h4 and MASK_26
        h0 += carry * 5
        carry = h0 ushr 26
        h0 = h0 and MASK_26
        h1 += carry

        var g0 = h0 + 5
        carry = g0 ushr 26
        g0 = g0 and MASK_26
        var g1 = h1 + carry
        carry = g1 ushr 26
        g1 = g1 and MASK_26
        var g2 = h2 + carry
        carry = g2 ushr 26
        g2 = g2 and MASK_26
        var g3 = h3 + carry
        carry = g3 ushr 26
        g3 = g3 and MASK_26
        val g4 = h4 + carry - (1L shl 26)
        if (g4 >= 0) {
            h0 = g0
            h1 = g1
            h2 = g2
            h3 = g3
            h4 = g4
        }

        val tag = ByteArray(TAG_SIZE)
        var f = (h0 or (h1 shl 26)) and UINT_MASK
        f += load32Long(key, 16)
        carry = f ushr 32
        store32(tag, 0, f)
        f = ((h1 ushr 6) or (h2 shl 20)) and UINT_MASK
        f += load32Long(key, 20) + carry
        carry = f ushr 32
        store32(tag, 4, f)
        f = ((h2 ushr 12) or (h3 shl 14)) and UINT_MASK
        f += load32Long(key, 24) + carry
        carry = f ushr 32
        store32(tag, 8, f)
        f = ((h3 ushr 18) or (h4 shl 8)) and UINT_MASK
        f += load32Long(key, 28) + carry
        store32(tag, 12, f)
        return tag
    }

    private fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (index in a.indices) {
            diff = diff or (a[index].toInt() xor b[index].toInt())
        }
        return diff == 0
    }

    private fun rotateLeft(
        value: Int,
        bits: Int,
    ): Int = (value shl bits) or (value ushr (32 - bits))

    private fun load32(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        bytes[offset].u() or
            (bytes[offset + 1].u() shl 8) or
            (bytes[offset + 2].u() shl 16) or
            (bytes[offset + 3].u() shl 24)

    private fun load32Long(
        bytes: ByteArray,
        offset: Int,
    ): Long = load32(bytes, offset).toLong() and UINT_MASK

    private fun store32(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }

    private fun store32(
        bytes: ByteArray,
        offset: Int,
        value: Long,
    ) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }

    private fun Byte.u(): Int = toInt() and 0xff

    private const val KEY_SIZE = 32
    private const val NONCE_SIZE = 24
    private const val TAG_SIZE = 16
    private const val AUTH_KEY_SIZE = 32
    private const val ZERO_PREFIX_SIZE = 32
    private const val MASK_26 = 0x3ffffffL
    private const val UINT_MASK = 0xffffffffL
    private val SIGMA = "expand 32-byte k".encodeToByteArray()
}
