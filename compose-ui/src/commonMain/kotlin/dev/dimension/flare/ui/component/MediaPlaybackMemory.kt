package dev.dimension.flare.ui.component

/** One in-memory progress record per media resource, shared by all pages and accounts. */
internal class MediaPlaybackMemory {
    private val positions = mutableMapOf<String, Double>()

    fun position(uri: String): Double = positions[uri] ?: 0.0

    fun save(
        uri: String,
        seconds: Double,
    ) {
        if (seconds.isFinite() && seconds >= 0) positions[uri] = seconds
    }

    companion object {
        val shared = MediaPlaybackMemory()
    }
}

internal class TimelineMediaSelections {
    private data class Collection(
        val urls: List<String>,
        val select: (String) -> Unit,
    )

    private val collections = mutableMapOf<Any, Collection>()
    private val selections = mutableMapOf<List<String>, String>()

    fun register(
        id: Any,
        urls: List<String>,
        select: (String) -> Unit,
    ) {
        collections[id] = Collection(urls.toList(), select)
        selections.remove(urls)?.let(select)
    }

    fun remove(id: Any) {
        collections.remove(id)
    }

    fun returned(
        urls: List<String>,
        selectedUri: String,
    ) {
        if (selectedUri !in urls) return
        val matching = collections.values.toList().filter { it.urls == urls }
        if (matching.isEmpty()) selections[urls.toList()] = selectedUri else selections.remove(urls)
        matching.forEach { it.select(selectedUri) }
    }
}
