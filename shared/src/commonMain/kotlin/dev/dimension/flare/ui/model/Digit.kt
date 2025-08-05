package dev.dimension.flare.ui.model

public data class Digit internal constructor(
    val digitChar: Char,
    val place: Int,
    val fullNumber: Long,
) {
    override fun equals(other: Any?): Boolean =
        when (other) {
            is Digit -> digitChar == other.digitChar
            else -> super.equals(other)
        }

    override fun hashCode(): Int {
        var result = digitChar.hashCode()
        result = 31 * result + place
        return result
    }
}
