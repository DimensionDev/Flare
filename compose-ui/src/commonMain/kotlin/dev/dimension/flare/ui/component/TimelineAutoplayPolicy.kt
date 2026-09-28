package dev.dimension.flare.ui.component

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.hypot

internal class TimelineAutoplayPolicy {
    data class Candidate(
        val id: Any,
        val groupId: Any? = null,
        val visible: Boolean,
        val selected: Boolean = true,
        val canStart: Boolean,
        // Distance from the complete video bounds to the viewport center, in dp.
        val distance: Float,
        val mediaUri: String? = null,
        val mediaIndex: Int? = null,
    )

    var activeId: Any? = null
        private set
    private var preferredGroupId: Any? = null
    private var preferredMediaIndex: Int? = null

    fun verticalScrollBegan() {
        preferredGroupId = null
        preferredMediaIndex = null
    }

    fun interactWithCarousel(groupId: Any) {
        preferredGroupId = groupId
        preferredMediaIndex = null
    }

    fun returnedToMedia(
        groupId: Any,
        mediaIndex: Int,
    ) {
        preferredGroupId = groupId
        preferredMediaIndex = mediaIndex
    }

    private fun matchesSelection(candidate: Candidate): Boolean =
        preferredMediaIndex?.let { candidate.mediaIndex == it } ?: candidate.selected

    fun select(
        candidates: Collection<Candidate>,
        scrolling: Boolean,
    ): Any? {
        val current = candidates.firstOrNull { it.id == activeId && it.visible }
        activeId =
            when {
                scrolling -> {
                    current?.id
                }

                preferredGroupId != null -> {
                    if (current != null && current.groupId == preferredGroupId && matchesSelection(current)) {
                        current.id
                    } else {
                        // A selected image deliberately leaves the timeline quiet.
                        candidates
                            .firstOrNull {
                                it.groupId == preferredGroupId && it.visible && matchesSelection(it) && it.canStart
                            }?.id
                    }
                }

                else -> {
                    val closest =
                        candidates
                            .filter { it.visible && it.selected && (it.canStart || it.id == current?.id) }
                            .minByOrNull { it.distance }
                    // Only near-ties retain the current video after scrolling settles.
                    if (current != null && current.selected && closest != null && current.distance <= closest.distance + 2f) {
                        current.id
                    } else {
                        closest?.id
                    }
                }
            }
        return activeId
    }

    companion object {
        fun centerDistance(
            bounds: Rect,
            viewport: Rect,
            multipleColumns: Boolean,
            density: Float,
        ): Float {
            val dy = bounds.center.y - viewport.center.y
            val pixels = if (multipleColumns) hypot(bounds.center.x - viewport.center.x, dy) else abs(dy)
            return pixels / density
        }
    }
}
