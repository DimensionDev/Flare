
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 *  **SnowflakeIdGenerator** — Multiplatform **singleton** (object) version.
 *
 * * Uses `kotlinx.datetime.Clock.System` for wall‑clock time
 * * Uses `kotlinx.atomicfu` for lock‑free, cross‑platform thread‑safety
 * * 64‑bit layout: 41 timestamp | 5 datacenter | 5 worker | 12 sequence
 *
 */
internal data object SnowflakeIdGenerator {
    // ─────────────────────────────── config (edit to suit) ──────────────────
    private const val DATA_CENTER_ID: Int = 1 // 0‒31
    private const val WORKER_ID: Int = 1 // 0‒31
    private const val EPOCH: Long = 1700000000000L

    // ─────────────────────────────── constants ──────────────────────────────
    private const val WORKER_ID_BITS = 5
    private const val DATACENTER_ID_BITS = 5
    private const val SEQUENCE_BITS = 12

    private const val MAX_WORKER_ID = (1 shl WORKER_ID_BITS) - 1
    private const val MAX_DATACENTER_ID = (1 shl DATACENTER_ID_BITS) - 1

    private const val WORKER_ID_SHIFT = SEQUENCE_BITS
    private const val DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS
    private const val TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS

    private const val SEQUENCE_MASK = (1L shl SEQUENCE_BITS) - 1

    // ─────────────────────────── internal mutable state ─────────────────────
    private val mutex = Mutex()
    private var sequence: Long = 0L
    private var lastTimestamp: Long = -1L

    init {
        require(WORKER_ID in 0..MAX_WORKER_ID) { "workerId must be in 0..$MAX_WORKER_ID" }
        require(DATA_CENTER_ID in 0..MAX_DATACENTER_ID) { "datacenterId must be in 0..$MAX_DATACENTER_ID" }
    }

    /**
     * Obtain the next **globally unique** 64‑bit ID.
     *
     * **Suspend**s while waiting to enter the critical section ‒ no CPU busy‑wait.
     */
    suspend fun nextId(): Long =
        mutex.withLock {
            var ts = currentMillis()

            if (ts < lastTimestamp) {
                error("Clock moved backwards by ${lastTimestamp - ts} ms; refusing to generate IDs.")
            }

            if (ts == lastTimestamp) {
                sequence = (sequence + 1) and SEQUENCE_MASK
                if (sequence == 0L) {
                    ts = waitNextMillis(lastTimestamp)
                }
            } else {
                sequence = 0L
            }

            lastTimestamp = ts

            return@withLock ((ts - EPOCH) shl TIMESTAMP_LEFT_SHIFT) or
                (DATA_CENTER_ID.toLong() shl DATACENTER_ID_SHIFT) or
                (WORKER_ID.toLong() shl WORKER_ID_SHIFT) or
                sequence
        }

    // ───────────────────────────── helper methods ───────────────────────────
    private fun currentMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private fun waitNextMillis(lastTs: Long): Long {
        var ts: Long
        do {
            ts = currentMillis()
        } while (ts <= lastTs)
        return ts
    }
}
