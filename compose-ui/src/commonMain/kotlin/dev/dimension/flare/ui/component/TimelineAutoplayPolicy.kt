package dev.dimension.flare.ui.component

internal class TimelineAutoplayPolicy {
    data class Candidate(
        val id: Any,
        val groupId: Any? = null,
        val visible: Boolean,
        val selected: Boolean = true,
        val canStart: Boolean,
        val distance: Float,
        val mediaUri: String? = null,
    )

    var activeId: Any? = null
        private set
    private var preferredGroupId: Any? = null
    private var preferredMediaUri: String? = null

    fun verticalScrollBegan() {
        preferredGroupId = null
        preferredMediaUri = null
    }

    fun interactWithCarousel(groupId: Any) {
        preferredGroupId = groupId
        preferredMediaUri = null
    }

    fun returnedToMedia(
        groupId: Any,
        mediaUri: String,
    ) {
        preferredGroupId = groupId
        preferredMediaUri = mediaUri
    }

    private fun matchesSelection(candidate: Candidate): Boolean = preferredMediaUri?.let { candidate.mediaUri == it } ?: candidate.selected

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

                current != null -> {
                    current.id
                }

                else -> {
                    candidates.filter { it.visible && it.selected && it.canStart }.minByOrNull { it.distance }?.id
                }
            }
        return activeId
    }
}
