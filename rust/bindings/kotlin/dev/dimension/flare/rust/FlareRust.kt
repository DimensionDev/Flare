@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package dev.dimension.flare.rust

private object Utf8Codec {
    fun maxBytes(value: String): Int = value.length * 3
}

private val <First, Second> Pair<First, Second>.field0: First get() = first
private val <First, Second> Pair<First, Second>.field1: Second get() = second
private val <First, Second, Third> Triple<First, Second, Third>.field0: First get() = first
private val <First, Second, Third> Triple<First, Second, Third>.field1: Second get() = second
private val <First, Second, Third> Triple<First, Second, Third>.field2: Third get() = third

class FfiException(message: String) : RuntimeException(message)

internal class BoltFfiErrorBufferException(val bytes: ByteArray) : RuntimeException("BoltFFI call failed")

private object DirectVectorCodec {
    fun readBooleanArray(bytes: ByteArray): BooleanArray =
        BooleanArray(bytes.size) { index -> bytes[index] != 0.toByte() }

    fun readByteArray(bytes: ByteArray): ByteArray = bytes

    fun writeBooleanArray(values: BooleanArray): ByteArray =
        ByteArray(values.size) { index -> if (values[index]) 1.toByte() else 0.toByte() }

    fun writeByteArray(values: ByteArray): ByteArray = values

    fun readShortArray(bytes: ByteArray): ShortArray {
        val values = ShortArray(elementCount(bytes, 2))
        nativeBuffer(bytes).asShortBuffer().get(values)
        return values
    }

    fun readUShortArray(bytes: ByteArray): UShortArray =
        readShortArray(bytes).toUShortArray()

    fun writeShortArray(values: ShortArray): ByteArray {
        val bytes = ByteArray(values.size * 2)
        nativeBuffer(bytes).asShortBuffer().put(values)
        return bytes
    }

    fun writeUShortArray(values: UShortArray): ByteArray =
        writeShortArray(values.asShortArray())

    fun readIntArray(bytes: ByteArray): IntArray {
        val values = IntArray(elementCount(bytes, 4))
        nativeBuffer(bytes).asIntBuffer().get(values)
        return values
    }

    fun readUIntArray(bytes: ByteArray): UIntArray =
        readIntArray(bytes).toUIntArray()

    fun writeIntArray(values: IntArray): ByteArray {
        val bytes = ByteArray(values.size * 4)
        nativeBuffer(bytes).asIntBuffer().put(values)
        return bytes
    }

    fun writeUIntArray(values: UIntArray): ByteArray =
        writeIntArray(values.asIntArray())

    fun readLongArray(bytes: ByteArray): LongArray {
        val values = LongArray(elementCount(bytes, 8))
        nativeBuffer(bytes).asLongBuffer().get(values)
        return values
    }

    fun readULongArray(bytes: ByteArray): ULongArray =
        readLongArray(bytes).toULongArray()

    fun writeLongArray(values: LongArray): ByteArray {
        val bytes = ByteArray(values.size * 8)
        nativeBuffer(bytes).asLongBuffer().put(values)
        return bytes
    }

    fun writeULongArray(values: ULongArray): ByteArray =
        writeLongArray(values.asLongArray())

    fun readFloatArray(bytes: ByteArray): FloatArray {
        val values = FloatArray(elementCount(bytes, 4))
        nativeBuffer(bytes).asFloatBuffer().get(values)
        return values
    }

    fun writeFloatArray(values: FloatArray): ByteArray {
        val bytes = ByteArray(values.size * 4)
        nativeBuffer(bytes).asFloatBuffer().put(values)
        return bytes
    }

    fun readDoubleArray(bytes: ByteArray): DoubleArray {
        val values = DoubleArray(elementCount(bytes, 8))
        nativeBuffer(bytes).asDoubleBuffer().get(values)
        return values
    }

    fun writeDoubleArray(values: DoubleArray): ByteArray {
        val bytes = ByteArray(values.size * 8)
        nativeBuffer(bytes).asDoubleBuffer().put(values)
        return bytes
    }

    private fun nativeBuffer(bytes: ByteArray): java.nio.ByteBuffer =
        java.nio.ByteBuffer
            .wrap(bytes)
            .order(java.nio.ByteOrder.nativeOrder())

    private fun elementCount(bytes: ByteArray, width: Int): Int {
        require(bytes.size % width == 0)
        return bytes.size / width
    }
}

internal class WireReader(private val bytes: ByteArray) {
    private var position = 0

    fun readBool(): Boolean = readI8() != 0.toByte()

    fun readI8(): Byte {
        val value = bytes[position]
        position += 1
        return value
    }

    fun readU8(): UByte = readI8().toUByte()

    fun readI16(): Short {
        val value =
            (bytes[position].toInt() and 0xff) or
                ((bytes[position + 1].toInt() and 0xff) shl 8)
        position += 2
        return value.toShort()
    }

    fun readU16(): UShort = readI16().toUShort()

    fun readI32(): Int {
        val value =
            (bytes[position].toInt() and 0xff) or
                ((bytes[position + 1].toInt() and 0xff) shl 8) or
                ((bytes[position + 2].toInt() and 0xff) shl 16) or
                ((bytes[position + 3].toInt() and 0xff) shl 24)
        position += 4
        return value
    }

    fun readU32(): UInt = readI32().toUInt()

    fun readI64(): Long {
        val low = readI32().toLong() and 0xffffffffL
        val high = readI32().toLong() and 0xffffffffL
        return low or (high shl 32)
    }

    fun readU64(): ULong = readI64().toULong()

    fun readF32(): Float = java.lang.Float.intBitsToFloat(readI32())

    fun readF64(): Double = java.lang.Double.longBitsToDouble(readI64())

    fun readOptionalBool(): Boolean? = readOptional { it.readBool() }

    fun readOptionalI8(): Byte? = readOptional { it.readI8() }

    fun readOptionalU8(): UByte? = readOptional { it.readU8() }

    fun readOptionalI16(): Short? = readOptional { it.readI16() }

    fun readOptionalU16(): UShort? = readOptional { it.readU16() }

    fun readOptionalI32(): Int? = readOptional { it.readI32() }

    fun readOptionalU32(): UInt? = readOptional { it.readU32() }

    fun readOptionalI64(): Long? = readOptional { it.readI64() }

    fun readOptionalU64(): ULong? = readOptional { it.readU64() }

    fun readOptionalF32(): Float? = readOptional { it.readF32() }

    fun readOptionalF64(): Double? = readOptional { it.readF64() }

    fun readString(): String {
        val length = readU32().toInt()
        val value = String(bytes, position, length, Charsets.UTF_8)
        position += length
        return value
    }

    fun readBytes(): ByteArray {
        val length = readU32().toInt()
        val value = bytes.copyOfRange(position, position + length)
        position += length
        return value
    }

    fun readBooleanArray(): BooleanArray {
        val length = readU32().toInt()
        return BooleanArray(length) { readBool() }
    }

    fun readByteArray(): ByteArray = readBytes()

    fun readShortArray(): ShortArray {
        val length = readU32().toInt()
        val byteCount = length * 2
        val values = ShortArray(length)
        java.nio.ByteBuffer
            .wrap(bytes, position, byteCount)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(values)
        position += byteCount
        return values
    }

    fun readUShortArray(): UShortArray =
        readShortArray().toUShortArray()

    fun readIntArray(): IntArray {
        val length = readU32().toInt()
        val byteCount = length * 4
        val values = IntArray(length)
        java.nio.ByteBuffer
            .wrap(bytes, position, byteCount)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asIntBuffer()
            .get(values)
        position += byteCount
        return values
    }

    fun readUIntArray(): UIntArray =
        readIntArray().toUIntArray()

    fun readLongArray(): LongArray {
        val length = readU32().toInt()
        val byteCount = length * 8
        val values = LongArray(length)
        java.nio.ByteBuffer
            .wrap(bytes, position, byteCount)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asLongBuffer()
            .get(values)
        position += byteCount
        return values
    }

    fun readULongArray(): ULongArray =
        readLongArray().toULongArray()

    fun readFloatArray(): FloatArray {
        val length = readU32().toInt()
        val byteCount = length * 4
        val values = FloatArray(length)
        java.nio.ByteBuffer
            .wrap(bytes, position, byteCount)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(values)
        position += byteCount
        return values
    }

    fun readDoubleArray(): DoubleArray {
        val length = readU32().toInt()
        val byteCount = length * 8
        val values = DoubleArray(length)
        java.nio.ByteBuffer
            .wrap(bytes, position, byteCount)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asDoubleBuffer()
            .get(values)
        position += byteCount
        return values
    }

    fun <T> readOptionalValue(read: (WireReader) -> T): T? = readOptional(read)

    fun <T> readSequence(read: (WireReader) -> T): List<T> {
        val length = readU32().toInt()
        return List(length) { read(this) }
    }

    fun <K, V> readMap(readKey: (WireReader) -> K, readValue: (WireReader) -> V): Map<K, V> {
        val length = readU32().toInt()
        val values = LinkedHashMap<K, V>(length)
        repeat(length) {
            val key = readKey(this)
            if (values.containsKey(key)) {
                throw IllegalArgumentException("duplicate map key")
            }
            values[key] = readValue(this)
        }
        return values
    }

    private inline fun <T> readOptional(read: (WireReader) -> T): T? {
        return when (readU8()) {
            0.toUByte() -> null
            1.toUByte() -> read(this)
            else -> throw IllegalArgumentException("invalid optional wire tag")
        }
    }
}

internal class WireWriter(initialCapacity: Int) {
    private var buffer = java.nio.ByteBuffer
        .allocateDirect(initialCapacity)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
    private var position = 0

    fun reset(requiredCapacity: Int) {
        if (buffer.capacity() < requiredCapacity) {
            buffer = java.nio.ByteBuffer
                .allocateDirect(requiredCapacity)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        }
        position = 0
    }

    fun toByteArray(): ByteArray {
        val bytes = ByteArray(position)
        val view = buffer.duplicate()
        view.position(0)
        view.get(bytes, 0, position)
        return bytes
    }

    fun directBuffer(): java.nio.ByteBuffer = buffer

    fun size(): Int = position

    fun writeBool(value: Boolean) {
        ensureCapacity(1)
        buffer.put(position, if (value) 1.toByte() else 0.toByte())
        position += 1
    }

    fun writeI8(value: Byte) {
        ensureCapacity(1)
        buffer.put(position, value)
        position += 1
    }

    fun writeU8(value: UByte) {
        writeI8(value.toByte())
    }

    fun writeI16(value: Short) {
        ensureCapacity(2)
        buffer.putShort(position, value)
        position += 2
    }

    fun writeU16(value: UShort) {
        writeI16(value.toShort())
    }

    fun writeI32(value: Int) {
        ensureCapacity(4)
        buffer.putInt(position, value)
        position += 4
    }

    fun writeU32(value: UInt) {
        writeI32(value.toInt())
    }

    fun writeI64(value: Long) {
        ensureCapacity(8)
        buffer.putLong(position, value)
        position += 8
    }

    fun writeU64(value: ULong) {
        writeI64(value.toLong())
    }

    fun writeF32(value: Float) {
        writeI32(java.lang.Float.floatToRawIntBits(value))
    }

    fun writeF64(value: Double) {
        writeI64(java.lang.Double.doubleToRawLongBits(value))
    }

    fun writeOptionalBool(value: Boolean?) = writeOptional(value) { writer, present ->
        writer.writeBool(present)
    }

    fun writeOptionalI8(value: Byte?) = writeOptional(value) { writer, present ->
        writer.writeI8(present)
    }

    fun writeOptionalU8(value: UByte?) = writeOptional(value) { writer, present ->
        writer.writeU8(present)
    }

    fun writeOptionalI16(value: Short?) = writeOptional(value) { writer, present ->
        writer.writeI16(present)
    }

    fun writeOptionalU16(value: UShort?) = writeOptional(value) { writer, present ->
        writer.writeU16(present)
    }

    fun writeOptionalI32(value: Int?) = writeOptional(value) { writer, present ->
        writer.writeI32(present)
    }

    fun writeOptionalU32(value: UInt?) = writeOptional(value) { writer, present ->
        writer.writeU32(present)
    }

    fun writeOptionalI64(value: Long?) = writeOptional(value) { writer, present ->
        writer.writeI64(present)
    }

    fun writeOptionalU64(value: ULong?) = writeOptional(value) { writer, present ->
        writer.writeU64(present)
    }

    fun writeOptionalF32(value: Float?) = writeOptional(value) { writer, present ->
        writer.writeF32(present)
    }

    fun writeOptionalF64(value: Double?) = writeOptional(value) { writer, present ->
        writer.writeF64(present)
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeU32(bytes.size.toUInt())
        writeBytesRaw(bytes)
    }

    fun writeBytes(value: ByteArray) {
        writeU32(value.size.toUInt())
        writeBytesRaw(value)
    }

    fun writeBooleanArray(values: BooleanArray) {
        writeU32(values.size.toUInt())
        values.forEach { writeBool(it) }
    }

    fun writeByteArray(values: ByteArray) = writeBytes(values)

    fun writeShortArray(values: ShortArray) {
        writeU32(values.size.toUInt())
        val byteCount = values.size * 2
        ensureCapacity(byteCount)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.asShortBuffer().put(values)
        position += byteCount
    }

    fun writeUShortArray(values: UShortArray) =
        writeShortArray(values.asShortArray())

    fun writeIntArray(values: IntArray) {
        writeU32(values.size.toUInt())
        val byteCount = values.size * 4
        ensureCapacity(byteCount)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.asIntBuffer().put(values)
        position += byteCount
    }

    fun writeUIntArray(values: UIntArray) =
        writeIntArray(values.asIntArray())

    fun writeLongArray(values: LongArray) {
        writeU32(values.size.toUInt())
        val byteCount = values.size * 8
        ensureCapacity(byteCount)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.asLongBuffer().put(values)
        position += byteCount
    }

    fun writeULongArray(values: ULongArray) =
        writeLongArray(values.asLongArray())

    fun writeFloatArray(values: FloatArray) {
        writeU32(values.size.toUInt())
        val byteCount = values.size * 4
        ensureCapacity(byteCount)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.asFloatBuffer().put(values)
        position += byteCount
    }

    fun writeDoubleArray(values: DoubleArray) {
        writeU32(values.size.toUInt())
        val byteCount = values.size * 8
        ensureCapacity(byteCount)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.asDoubleBuffer().put(values)
        position += byteCount
    }

    fun <T> writeOptionalValue(value: T?, write: (WireWriter, T) -> Unit) {
        writeOptional(value, write)
    }

    fun <T> writeSequence(value: Iterable<T>, count: Int, write: (WireWriter, T) -> Unit) {
        writeU32(count.toUInt())
        value.forEach { item -> write(this, item) }
    }

    fun <K, V> writeMap(
        value: Map<K, V>,
        writeKey: (WireWriter, K) -> Unit,
        writeValue: (WireWriter, V) -> Unit,
    ) {
        writeU32(value.size.toUInt())
        value.entries.forEach { entry ->
            writeKey(this, entry.key)
            writeValue(this, entry.value)
        }
    }

    private fun writeBytesRaw(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.put(bytes)
        position += bytes.size
    }

    private fun ensureCapacity(needed: Int) {
        val required = position + needed
        if (required <= buffer.capacity()) {
            return
        }
        val nextCapacity = maxOf(buffer.capacity() * 2, required)
        val next = java.nio.ByteBuffer
            .allocateDirect(nextCapacity)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val source = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        source.limit(position)
        source.position(0)
        next.put(source)
        buffer = next
    }

    private inline fun <T> writeOptional(value: T?, write: (WireWriter, T) -> Unit) {
        if (value == null) {
            writeU8(0.toUByte())
            return
        }
        writeU8(1.toUByte())
        write(this, value)
    }
}

private const val MAX_CACHED_WIRE_WRITER_BYTES: Int = 1024 * 1024

private class WireWriterPoolState(private val cacheSize: Int = 4) {
    private val cachedWriters: Array<WireWriter?> = arrayOfNulls(cacheSize)
    private var depth = 0

    fun acquire(requiredCapacity: Int): BorrowedWireWriter {
        val slot = depth
        depth = slot + 1
        val shouldCache = requiredCapacity <= MAX_CACHED_WIRE_WRITER_BYTES && slot < cacheSize
        val writer = if (shouldCache) {
            cachedWriters[slot] ?: WireWriter(requiredCapacity).also { cachedWriters[slot] = it }
        } else {
            WireWriter(requiredCapacity)
        }

        writer.reset(requiredCapacity)
        return BorrowedWireWriter(this, writer)
    }

    fun release() {
        depth -= 1
    }
}

private class BorrowedWireWriter(
    private val state: WireWriterPoolState,
    val writer: WireWriter,
) : AutoCloseable {
    fun bytes(): ByteArray = writer.toByteArray()

    fun directBuffer(): java.nio.ByteBuffer = writer.directBuffer()

    fun size(): Int = writer.size()

    override fun close() {
        state.release()
    }
}

private object WireWriterPool {
    private val state: ThreadLocal<WireWriterPoolState> =
        ThreadLocal.withInitial { WireWriterPoolState() }

    fun acquire(requiredCapacity: Int): BorrowedWireWriter {
        val poolState = state.get() ?: WireWriterPoolState().also { state.set(it) }
        return poolState.acquire(requiredCapacity)
    }
}

private inline fun <K, V> Map<K, V>.wireSize(
    keySize: (K) -> Int,
    valueSize: (V) -> Int,
): Int = 4 + entries.sumOf { entry -> keySize(entry.key) + valueSize(entry.value) }

@Suppress("FunctionName")
private object Native {
    init {
        val androidLibrary = "flare-ffi"
        val desktopPreferredLibrary = "flare_ffi_jni"
        val desktopFallbackLibrary = "flare_ffi"
        val vmName = System.getProperty("java.vm.name").orEmpty()
        val isAndroidRuntime =
            vmName.contains("dalvik", ignoreCase = true) ||
            vmName.contains("art", ignoreCase = true)
        if (isAndroidRuntime) {
            System.loadLibrary(androidLibrary)
        } else {
            loadDesktopLibraries(desktopPreferredLibrary, desktopFallbackLibrary)
        }
    }

    @Volatile
    private var bundledLibraryDirectory: java.io.File? = null

    private fun loadDesktopLibraries(preferredLibrary: String, fallbackLibrary: String) {
        var preferredFailure = tryLoadDesktopLibrary(preferredLibrary)
        if (preferredFailure == null) {
            return
        }

        if (tryLoadOptionalDesktopLibrary(fallbackLibrary)) {
            preferredFailure = tryLoadDesktopLibrary(preferredLibrary)
            if (preferredFailure == null) {
                return
            }
        }

        throw preferredFailure
    }

    private fun tryLoadDesktopLibrary(libraryName: String): UnsatisfiedLinkError? {
        try {
            if (loadBundledLibraryIfPresent(libraryName) || loadExternalLibraryIfPresent(libraryName)) {
                return null
            }
            return UnsatisfiedLinkError("Could not load native library '$libraryName'")
        } catch (error: UnsatisfiedLinkError) {
            return error
        }
    }

    private fun tryLoadOptionalDesktopLibrary(libraryName: String): Boolean {
        return try {
            loadBundledLibraryIfPresent(libraryName) || loadExternalLibraryIfPresent(libraryName)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    private fun loadExternalLibraryIfPresent(libraryName: String): Boolean {
        return try {
            System.loadLibrary(libraryName)
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    private fun loadBundledLibraryIfPresent(libraryName: String): Boolean {
        val mappedName = System.mapLibraryName(libraryName)
        for (resourcePath in bundledLibraryResourceCandidates(mappedName)) {
            Native::class.java.getResourceAsStream(resourcePath)?.use { input ->
                val extracted = extractBundledLibrary(resourcePath, input)
                System.load(extracted.absolutePath)
                return true
            }
        }
        return false
    }

    private fun extractBundledLibrary(
        resourcePath: String,
        input: java.io.InputStream,
    ): java.io.File {
        val fileName = resourcePath.substringAfterLast('/')
        val extracted = java.io.File(bundledLibraryDirectory(), fileName)
        if (!extracted.isFile) {
            java.io.FileOutputStream(extracted).use { output ->
                input.copyTo(output)
            }
            extracted.deleteOnExit()
        }
        return extracted
    }

    private fun bundledLibraryDirectory(): java.io.File {
        bundledLibraryDirectory?.let { return it }
        synchronized(this) {
            bundledLibraryDirectory?.let { return it }
            val created = java.io.File.createTempFile("boltffi-native-", "")
            if (!created.delete() || !created.mkdir()) {
                throw java.io.IOException("failed to create temp directory for bundled native extraction")
            }
            created.deleteOnExit()
            bundledLibraryDirectory = created
            return created
        }
    }

    private fun bundledLibraryResourceCandidates(mappedName: String): List<String> {
        val candidates = mutableListOf<String>()
        for (directory in desktopNativeDirectories()) {
            candidates += "/$directory/$mappedName"
            candidates += "/native/$directory/$mappedName"
        }
        candidates += "/$mappedName"
        return candidates
    }

    private fun desktopNativeDirectories(): List<String> {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        return when {
            (osName.contains("mac") || osName.contains("darwin")) &&
                (osArch == "aarch64" || osArch == "arm64") ->
                listOf("darwin-arm64", "darwin-aarch64")
            (osName.contains("mac") || osName.contains("darwin")) &&
                (osArch == "x86_64" || osArch == "amd64") ->
                listOf("darwin-x86_64", "darwin-x86-64")
            (osName.contains("linux")) &&
                (osArch == "x86_64" || osArch == "amd64") ->
                listOf("linux-x86_64", "linux-x86-64")
            (osName.contains("linux")) &&
                (osArch == "aarch64" || osArch == "arm64") ->
                listOf("linux-aarch64", "linux-arm64")
            (osName.contains("windows")) &&
                (osArch == "x86_64" || osArch == "amd64") ->
                listOf("windows-x86_64", "windows-x86-64", "win32-x86_64")
            (osName.contains("windows")) &&
                (osArch == "aarch64" || osArch == "arm64") ->
                listOf("windows-aarch64", "windows-arm64", "win32-arm64")
            else -> emptyList()
        }
    }
    @JvmStatic external fun boltffi_release_class_flare_ffi_counter_core(handle: Long): Unit
    @JvmStatic external fun boltffi_method_class_flare_ffi_counter_core_update(`receiver`: Long, event: java.nio.ByteBuffer, __boltffi_event_len: Int): ByteArray?
    @JvmStatic external fun boltffi_method_class_flare_ffi_counter_core_view(`receiver`: Long): ByteArray?
    @JvmStatic external fun boltffi_function_flare_ffi_create_counter_core(initial_value: Long): Long
}

class CounterCore internal constructor(internal val handle: Long) : AutoCloseable {
    private val __boltffi_closed = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun close() {
        if (__boltffi_closed.compareAndSet(false, true)) {
            Native.boltffi_release_class_flare_ffi_counter_core(handle)
        }
    }

    internal fun boltffiHandle(): Long {
        check(!__boltffi_closed.get()) { "CounterCore is closed" }
        return handle
    }

    fun update(event: ByteArray): ByteArray {
        val __boltffi_event_wire = WireWriterPool.acquire(4 + event.size)
        val __boltffi_event_writer = __boltffi_event_wire.writer
        __boltffi_event_writer.writeBytes(event)
        try {
            val __boltffi_result = try { Native.boltffi_method_class_flare_ffi_counter_core_update(this.boltffiHandle(), __boltffi_event_wire.directBuffer(), __boltffi_event_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw FfiException(__boltffi_error_reader.readString()) } } ?: throw IllegalStateException("null buffer returned")
            val __boltffi_reader = WireReader(__boltffi_result)
            return __boltffi_reader.readBytes()
        } finally {
            __boltffi_event_wire.close()
        }
    }

    fun view(): ByteArray {
        val __boltffi_result = try { Native.boltffi_method_class_flare_ffi_counter_core_view(this.boltffiHandle()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw FfiException(__boltffi_error_reader.readString()) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return __boltffi_reader.readBytes()
    }
}

fun createCounterCore(initialValue: Long): CounterCore {
    return CounterCore(Native.boltffi_function_flare_ffi_create_counter_core(initialValue))
}