package dev.dimension.flare.ui.component

internal class VideoPlaybackArbiter {
    private data class Client(
        val stop: () -> Unit,
        val reconsider: () -> Unit,
        val mediaReturned: (List<String>, String) -> Unit,
        val resume: () -> Unit,
        val willHandoff: (String) -> Unit,
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
        resume: () -> Unit = reconsider,
        willHandoff: (String) -> Unit = {},
    ) {
        clients[owner] = Client(stop, reconsider, mediaReturned, resume, willHandoff)
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
        if (active === owner) stopActive(if (wasTop) selectedUri else null)
        if (wasTop && selectedUri != null) clients[returnOwner]?.mediaReturned?.invoke(mediaUrls, selectedUri)
        if (preferred === owner) preferred = returnOwner?.takeIf { it in clients }
        returnOwners.keys.filter { returnOwners[it] === owner }.forEach { returnOwners[it] = returnOwner }
        presentations.remove(owner)
        clients.remove(owner)
        reconsider(if (wasTop) returnOwner else null)
    }

    fun present(
        owner: Any,
        selectedUri: String? = null,
    ) {
        if (owner in presentations) return
        returnOwners[owner] = preferred ?: active
        presentations.add(owner)
        preferred = owner
        stopActive(selectedUri)
    }

    private fun stopActive(continuingToUri: String? = null) {
        val old = active
        active = null
        clients[old]?.let { client ->
            continuingToUri?.let(client.willHandoff)
            client.stop()
        }
    }

    private fun reconsider(immediateOwner: Any? = null) {
        clients.toList().forEach { (owner, client) ->
            if (owner === immediateOwner) client.resume() else client.reconsider()
        }
    }

    companion object {
        val shared = VideoPlaybackArbiter()
    }
}
