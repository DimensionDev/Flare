package dev.dimension.flare.ui.component

internal class VideoPlaybackArbiter {
    private data class Client(
        val stop: () -> Unit,
        val reconsider: () -> Unit,
    )

    private val clients = mutableMapOf<Any, Client>()
    private var active: Any? = null
    private var preferred: Any? = null
    private val presentations = mutableListOf<Any>()

    fun register(
        owner: Any,
        stop: () -> Unit,
        reconsider: () -> Unit,
    ) {
        clients[owner] = Client(stop, reconsider)
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

    fun remove(owner: Any) {
        if (active === owner) stopActive()
        if (preferred === owner) preferred = null
        presentations.remove(owner)
        clients.remove(owner)
        reconsider()
    }

    fun present(owner: Any) {
        presentations.remove(owner)
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
