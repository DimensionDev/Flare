package dev.dimension.flare.media

import kotlinx.serialization.Serializable

@Serializable
public data class UgoiraFrame(
    val file: String,
    val delayMillis: Int,
)

@Serializable
public data class UgoiraMetadata(
    val zipUrl: String,
    val frames: List<UgoiraFrame>,
) {
    public fun validate() {
        require(frames.isNotEmpty() && frames.size <= 10_000) { "Invalid Ugoira frame count" }
        require(frames.map { it.file }.distinct().size == frames.size) { "Duplicate Ugoira frames" }
        require(frames.all { it.delayMillis in 1..60_000 && it.file.matches(Regex("[A-Za-z0-9_-]+\\.(jpg|jpeg|png|gif)")) }) {
            "Invalid Ugoira frame name or duration"
        }
        require(frames.sumOf { it.delayMillis.toLong() } <= 3_600_000) { "Ugoira is too long" }
    }
}

/** Local compressed images, in metadata order. Release through UgoiraPresenter when no longer in use. */
public class UgoiraAnimation(
    public val key: String,
    public val frames: List<UgoiraFrame>,
    public val quality: String,
) {
    init {
        require(frames.isNotEmpty() && frames.all { it.delayMillis > 0 })
    }

    private val ends = frames.runningFold(0L) { time, frame -> time + frame.delayMillis }.drop(1)
    public val durationMillis: Long = ends.last()

    public fun frameIndex(positionMillis: Long): Int {
        val position = ((positionMillis % durationMillis) + durationMillis) % durationMillis
        val index = ends.binarySearch(position)
        return if (index >= 0) index + 1 else -index - 1
    }

    public fun frameStartMillis(index: Int): Long = if (index == 0) 0 else ends[index - 1]
}
