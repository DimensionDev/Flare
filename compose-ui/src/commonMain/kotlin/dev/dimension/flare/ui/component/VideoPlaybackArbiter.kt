package dev.dimension.flare.ui.component

internal class VideoPlaybackArbiter {
    private data class Client(
        val stop: () -> Unit,
        val reconsider: () -> Unit,
        val mediaReturned: (List<String>, String) -> Unit,
    )

    private val clients = mutableMapOf<Any, Client>()
    private var active: Any? = null
    private var preferred: Any? = null
    private val presentations = mutableListOf<Any>()
    private val returnOwners = mutableMapOf<Any, Any?>()

    fun register(
        owner: Any,
        stop: () -> Unit,
        reconsider: () -> Unit,
        mediaReturned: (List<String>, String) -> Unit = { _, _ -> },
    ) {
        clients[owner] = Client(stop, reconsider, mediaReturned)
    }

    fun interacted(owner: Any) {
        preferred = owner
    }

    fun acquire(owner: Any): Boolean {
        if (presentations.lastOrNull()?.let { it !== owner } == true) return false
        if (active === owner) return true
        if (preferred != null && preferred !== owner && presentations.lastOrNull() !== owner) return false
        if (active != null && preferred !== owner && presentations.lastOrNull() !== owner) return false
        stopActive()
        active = owner
        return true
    }

    fun settledWithoutVideo(owner: Any) {
        if (presentations.lastOrNull()?.let { it !== owner } == true) return
        if (preferred === owner || active === owner) stopActive()
    }

    fun release(owner: Any) {
        if (active !== owner) return
        stopActive()
        reconsider()
    }

    fun remove(
        owner: Any,
        mediaUrls: List<String> = emptyList(),
        selectedUri: String? = null,
    ) {
        val wasTop = presentations.lastOrNull() === owner
        val returnOwner = returnOwners.remove(owner)
        if (active === owner) stopActive()
        if (wasTop && selectedUri != null) clients[returnOwner]?.mediaReturned?.invoke(mediaUrls, selectedUri)
        if (preferred === owner) preferred = returnOwner?.takeIf { it in clients }
        returnOwners.keys.filter { returnOwners[it] === owner }.forEach { returnOwners[it] = returnOwner }
        presentations.remove(owner)
        clients.remove(owner)
        reconsider()
    }

    fun present(owner: Any) {
        if (owner in presentations) return
        returnOwners[owner] = preferred ?: active
        presentations.add(owner)
        preferred = owner
        stopActive()
    }

    private fun stopActive() {
        val old = active
        active = null
        clients[old]?.stop?.invoke()
    }

    private fun reconsider() {
        clients.values.toList().forEach { it.reconsider() }
    }

    companion object {
        val shared = VideoPlaybackArbiter()
    }
}
