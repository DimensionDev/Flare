package dev.dimension.flare.data.network.nostr

private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

internal fun bech32PublicKey(hex: String): String {
    val bytes = hexToBytes(hex)
    val data = convertBits(bytes, fromBits = 8, toBits = 5, pad = true)
    val checksum = createChecksum("npub", data)
    return buildString {
        append("npub1")
        (data + checksum).forEach {
            append(BECH32_CHARSET[it])
        }
    }
}

private fun hexToBytes(hex: String): List<Int> {
    require(hex.length == 64) { "Nostr public key must be 32 bytes" }
    return hex.chunked(2).map { it.toInt(radix = 16) }
}

private fun convertBits(
    data: List<Int>,
    fromBits: Int,
    toBits: Int,
    pad: Boolean,
): List<Int> {
    var accumulator = 0
    var bits = 0
    val maxValue = (1 shl toBits) - 1
    val result = mutableListOf<Int>()
    data.forEach { value ->
        require(value >= 0 && value shr fromBits == 0) { "Invalid Bech32 source value" }
        accumulator = (accumulator shl fromBits) or value
        bits += fromBits
        while (bits >= toBits) {
            bits -= toBits
            result += (accumulator shr bits) and maxValue
        }
    }
    if (pad && bits > 0) {
        result += (accumulator shl (toBits - bits)) and maxValue
    } else if (!pad) {
        require(bits < fromBits && ((accumulator shl (toBits - bits)) and maxValue) == 0) {
            "Invalid Bech32 padding"
        }
    }
    return result
}

private fun createChecksum(
    humanReadablePart: String,
    data: List<Int>,
): List<Int> {
    val values = expandHumanReadablePart(humanReadablePart) + data + List(6) { 0 }
    val polymod = polymod(values) xor 1
    return (0 until 6).map { index ->
        (polymod shr (5 * (5 - index))) and 31
    }
}

private fun expandHumanReadablePart(value: String): List<Int> =
    value.map { it.code shr 5 } + 0 + value.map { it.code and 31 }

private fun polymod(values: List<Int>): Int {
    val generators = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    var checksum = 1
    values.forEach { value ->
        val top = checksum shr 25
        checksum = ((checksum and 0x1ffffff) shl 5) xor value
        generators.forEachIndexed { index, generator ->
            if (((top shr index) and 1) != 0) {
                checksum = checksum xor generator
            }
        }
    }
    return checksum
}
