@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.data.network

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSLock
import platform.posix.AF_INET
import platform.posix.EINTR
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_SNDTIMEO
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.errno
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.memset
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.timeval

internal actual class MediaRelayLock actual constructor() {
    private val lock = NSLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}

internal actual class MediaRelayListener actual constructor() {
    private val descriptor = socket(AF_INET, SOCK_STREAM, 0).also { check(it >= 0) { "Cannot create media listener" } }
    actual val port: Int

    actual fun close() {
        close(descriptor)
    }

    init {
        try {
            port =
                memScoped {
                    val address = alloc<sockaddr_in>()
                    memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
                    address.sin_len = sizeOf<sockaddr_in>().toUByte()
                    address.sin_family = AF_INET.toUByte()
                    address.sin_addr.s_addr = platform.CoreFoundation.CFSwapInt32HostToBig(0x7f000001u)
                    address.sin_port = 0u
                    check(
                        bind(descriptor, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0,
                    ) { "Cannot bind media listener" }
                    check(listen(descriptor, 64) == 0) { "Cannot start media listener" }
                    val length = alloc<socklen_tVar> { value = sizeOf<sockaddr_in>().convert() }
                    check(getsockname(descriptor, address.ptr.reinterpret(), length.ptr) == 0)
                    platform.CoreFoundation.CFSwapInt16BigToHost(address.sin_port).toInt()
                }
        } catch (error: Throwable) {
            close(descriptor)
            throw error
        }
    }

    actual fun accept(): MediaRelayConnection {
        var accepted: Int
        do {
            accepted = accept(descriptor, null, null)
        } while (accepted < 0 && errno == EINTR)
        check(accepted >= 0) { "Cannot accept media request" }
        memScoped {
            val enabled = alloc<IntVar> { value = 1 }
            setsockopt(accepted, SOL_SOCKET, SO_NOSIGPIPE, enabled.ptr, sizeOf<IntVar>().convert())
            val timeout =
                alloc<timeval> {
                    tv_sec = 30
                    tv_usec = 0
                }
            setsockopt(accepted, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
            setsockopt(accepted, SOL_SOCKET, SO_SNDTIMEO, timeout.ptr, sizeOf<timeval>().convert())
        }
        return object : MediaRelayConnection {
            override fun read(bytes: ByteArray): Int =
                bytes.usePinned {
                    var count: Long
                    do {
                        count = recv(accepted, it.addressOf(0), bytes.size.convert(), 0)
                    } while (count < 0 && errno == EINTR)
                    check(count >= 0) { "Media request read failed" }
                    if (count == 0L) -1 else count.toInt()
                }

            override fun write(
                bytes: ByteArray,
                count: Int,
            ) {
                if (count == 0) return
                bytes.usePinned {
                    var offset = 0
                    while (offset < count) {
                        val written = send(accepted, it.addressOf(offset), (count - offset).convert(), 0)
                        if (written < 0 && errno == EINTR) continue
                        check(written > 0) { "Media response write failed" }
                        offset += written.toInt()
                    }
                }
            }

            override fun close() {
                platform.posix.close(accepted)
            }
        }
    }
}
