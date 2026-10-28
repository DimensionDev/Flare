package dev.dimension.flare.feature.plugin.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll

/** Retains caller jobs until their cache/credential writes have also finished. */
internal class PluginSessionV1 {
    private data class State(
        val active: Boolean = true,
        val jobs: Set<Job> = emptySet(),
        val writes: Set<Job> = emptySet(),
        val cleanups: Set<suspend () -> Unit> = emptySet(),
    )

    private val state = MutableStateFlow(State())
    val isActive: Boolean get() = state.value.active

    fun onRetire(cleanup: suspend () -> Unit): () -> Unit {
        state.update {
            if (!it.active) throw PluginSessionRetiredException()
            it.copy(cleanups = it.cleanups + cleanup)
        }
        return { state.update { it.copy(cleanups = it.cleanups - cleanup) } }
    }

    fun ensureActive() {
        if (!state.value.active) throw PluginSessionRetiredException()
    }

    fun scope(parent: CoroutineScope): CoroutineScope {
        val job = SupervisorJob(parent.coroutineContext[Job])
        try {
            track(job)
        } catch (error: Throwable) {
            job.cancel()
            throw error
        }
        return CoroutineScope(parent.coroutineContext + job)
    }

    private fun track(job: Job) {
        while (true) {
            val before = state.value
            if (!before.active) throw PluginSessionRetiredException()
            if (job in before.jobs) return
            if (state.compareAndSet(before, before.copy(jobs = before.jobs + job))) {
                job.invokeOnCompletion { state.update { it.copy(jobs = it.jobs - job, writes = it.writes - job) } }
                return
            }
        }
    }

    suspend fun <T> invoke(
        method: String,
        block: suspend () -> T,
    ): T {
        val caller = currentCoroutineContext()[Job]
        caller?.let(::track)
        state.update {
            if (!it.active) throw PluginSessionRetiredException()
            if (caller != null && method.substringAfterLast('.') in WRITE_OPERATIONS) it.copy(writes = it.writes + caller) else it
        }
        return block().also {
            ensureActive()
            currentCoroutineContext().ensureActive()
        }
    }

    suspend fun retire() {
        var jobs: Set<Job>
        var cleanups: Set<suspend () -> Unit>
        while (true) {
            val before = state.value
            check(before.writes.isEmpty()) { "Wait for publishing, uploads or other write operations to finish before reloading" }
            if (!before.active) return
            if (state.compareAndSet(before, before.copy(active = false))) {
                jobs = before.jobs
                cleanups = before.cleanups
                break
            }
        }
        val caller = currentCoroutineContext()[Job]
        jobs = jobs.filterTo(mutableSetOf()) { it !== caller }
        jobs.forEach { it.cancel(PluginSessionRetiredException()) }
        jobs.toList().joinAll()
        cleanups.forEach { cleanup -> runCatching { cleanup() } }
        state.update { it.copy(cleanups = emptySet()) }
    }
}

internal class PluginSessionRetiredException : CancellationException("Plugin session was reloaded")

private val WRITE_OPERATIONS = setOf("publish", "mutate", "delete", "send", "leave", "create", "update", "addMember", "removeMember")
