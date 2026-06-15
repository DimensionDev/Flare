package dev.dimension.flare.data.network.xqt.xchat

internal typealias XChatThriftStruct = Map<Int, XChatThriftValue>

internal sealed interface XChatThriftValue {
    data class Bool(
        val value: Boolean,
    ) : XChatThriftValue

    data class I32(
        val value: Int,
    ) : XChatThriftValue

    data class I64(
        val value: Long,
    ) : XChatThriftValue

    data class Binary(
        val bytes: ByteArray,
    ) : XChatThriftValue

    data class Struct(
        val fields: XChatThriftStruct,
    ) : XChatThriftValue

    data class ListValue(
        val values: List<XChatThriftValue>,
    ) : XChatThriftValue
}

internal object XChatThrift {
    fun parse(bytes: ByteArray): XChatThriftStruct = readStruct(bytes, 0).first

    fun readLeadingSequenceId(base64: String?): String? =
        runCatching {
            val bytes = XChatBase64.decode(base64 ?: return null)
            if (bytes.size < 7 || bytes[0].u() != T_STRING || bytes[1].u() != 0 || bytes[2].u() != 1) {
                return null
            }
            val length = readI32(bytes, 3)
            bytes.copyOfRange(7, 7 + length).decodeToString()
        }.getOrNull()

    fun readStringField(
        base64: String?,
        fieldId: Int,
    ): String? =
        runCatching {
            val bytes = XChatBase64.decode(base64 ?: return null)
            var offset = 0
            while (offset < bytes.size) {
                val type = bytes[offset++].u()
                if (type == T_STOP) break
                val id = (bytes[offset++].u() shl 8) or bytes[offset++].u()
                if (type == T_STRING) {
                    val length = readI32(bytes, offset)
                    offset += 4
                    val value = bytes.copyOfRange(offset, offset + length).decodeToString()
                    offset += length
                    if (id == fieldId) return value
                } else {
                    offset = skipValue(bytes, offset, type)
                }
            }
            null
        }.getOrNull()

    fun textHolder(text: String): ByteArray =
        holder(
            variantId = 1,
            structBytes = fStr(1, text) + STOP,
        )

    fun reactionAddHolder(
        sequenceId: String,
        emoji: String,
        attachmentId: String? = null,
    ): ByteArray =
        reactionHolder(
            variantId = 2,
            sequenceId = sequenceId,
            emoji = emoji,
            attachmentId = attachmentId,
        )

    fun reactionRemoveHolder(
        sequenceId: String,
        emoji: String,
        attachmentId: String? = null,
    ): ByteArray =
        reactionHolder(
            variantId = 3,
            sequenceId = sequenceId,
            emoji = emoji,
            attachmentId = attachmentId,
        )

    fun editHolder(
        sequenceId: String,
        updatedText: String,
    ): ByteArray =
        holder(
            variantId = 4,
            structBytes = fStr(1, sequenceId) + fStr(2, updatedText) + STOP,
        )

    fun markReadHolder(
        sequenceId: String,
        seenAtMillis: Long,
    ): ByteArray =
        holder(
            variantId = 5,
            structBytes = fStr(1, sequenceId) + fI64(2, seenAtMillis) + STOP,
        )

    fun markUnreadHolder(sequenceId: String): ByteArray =
        holder(
            variantId = 6,
            structBytes = fStr(1, sequenceId) + STOP,
        )

    fun pinHolder(conversationId: String): ByteArray =
        holder(
            variantId = 7,
            structBytes = fStr(1, conversationId) + STOP,
        )

    fun unpinHolder(conversationId: String): ByteArray =
        holder(
            variantId = 8,
            structBytes = fStr(1, conversationId) + STOP,
        )

    fun screenCaptureHolder(type: ScreenCaptureType): ByteArray =
        holder(
            variantId = 9,
            structBytes = fI32(1, type.value) + STOP,
        )

    fun nicknameHolder(
        userId: String,
        nickname: String,
    ): ByteArray =
        holder(
            variantId = 14,
            structBytes = fI64(1, userId.toLong()) + fStr(2, nickname) + STOP,
        )

    fun messageCreateEvent(
        frame: ByteArray,
        conversationKeyVersion: String,
        shouldNotify: Boolean = true,
        ttlMsec: Long? = null,
    ): ByteArray =
        fBin(100, frame) +
            fStr(101, conversationKeyVersion) +
            fBool(102, shouldNotify) +
            (ttlMsec?.let { fI64(103, it) } ?: ByteArray(0)) +
            STOP

    fun eventSignature(
        signatureBase64Url: String,
        publicKeyVersion: String,
        signingPublicKeyB64: String,
        senderId: String,
    ): ByteArray {
        val keyInfo =
            fStr(1, senderId) +
                fStr(2, publicKeyVersion) +
                fStr(3, signingPublicKeyB64) +
                STOP
        return fStr(1, signatureBase64Url) +
            fStr(2, publicKeyVersion) +
            fStr(3, "7") +
            fStr(4, signingPublicKeyB64) +
            listOfStructs(5, listOf(keyInfo)) +
            STOP
    }

    fun deleteEventDetail(
        sequenceIds: List<String>,
        actionValue: Int,
    ): ByteArray {
        val deleteEvent = listOfStrings(1, sequenceIds) + fI32(2, actionValue) + STOP
        return fStruct(7, deleteEvent) + STOP
    }

    private fun holder(
        variantId: Int,
        structBytes: ByteArray,
    ): ByteArray {
        val entryContents = fStruct(variantId, structBytes) + STOP
        return fStruct(1, entryContents) + STOP
    }

    private fun reactionHolder(
        variantId: Int,
        sequenceId: String,
        emoji: String,
        attachmentId: String?,
    ): ByteArray =
        holder(
            variantId = variantId,
            structBytes =
                fStr(1, sequenceId) +
                    fStr(2, emoji) +
                    (attachmentId?.let { fStr(3, it) } ?: ByteArray(0)) +
                    STOP,
        )

    private fun readStruct(
        bytes: ByteArray,
        start: Int,
    ): Pair<XChatThriftStruct, Int> {
        val fields = mutableMapOf<Int, XChatThriftValue>()
        var offset = start
        while (offset < bytes.size) {
            val type = bytes[offset++].u()
            if (type == T_STOP) break
            val fieldId = (bytes[offset++].u() shl 8) or bytes[offset++].u()
            val (value, nextOffset) = readValue(bytes, offset, type)
            fields[fieldId] = value
            offset = nextOffset
        }
        return fields to offset
    }

    private fun readValue(
        bytes: ByteArray,
        offset: Int,
        type: Int,
    ): Pair<XChatThriftValue, Int> =
        when (type) {
            T_BOOL -> XChatThriftValue.Bool(bytes[offset].u() != 0) to offset + 1
            T_I32 -> XChatThriftValue.I32(readI32(bytes, offset)) to offset + 4
            T_I64 -> XChatThriftValue.I64(readI64(bytes, offset)) to offset + 8
            T_STRING -> {
                val length = readI32(bytes, offset)
                val start = offset + 4
                XChatThriftValue.Binary(bytes.copyOfRange(start, start + length)) to start + length
            }
            T_STRUCT -> {
                val (struct, nextOffset) = readStruct(bytes, offset)
                XChatThriftValue.Struct(struct) to nextOffset
            }
            T_LIST -> {
                val elementType = bytes[offset].u()
                val count = readI32(bytes, offset + 1)
                var currentOffset = offset + 5
                val values = buildList {
                    repeat(count) {
                        val (value, nextOffset) = readValue(bytes, currentOffset, elementType)
                        add(value)
                        currentOffset = nextOffset
                    }
                }
                XChatThriftValue.ListValue(values) to currentOffset
            }
            else -> error("Unknown thrift type $type at $offset")
        }

    private fun skipValue(
        bytes: ByteArray,
        offset: Int,
        type: Int,
    ): Int =
        when (type) {
            T_BOOL -> offset + 1
            T_I32 -> offset + 4
            T_I64 -> offset + 8
            T_STRING -> offset + 4 + readI32(bytes, offset)
            T_STRUCT -> readStruct(bytes, offset).second
            T_LIST -> {
                val elementType = bytes[offset].u()
                val count = readI32(bytes, offset + 1)
                var currentOffset = offset + 5
                repeat(count) {
                    currentOffset = skipValue(bytes, currentOffset, elementType)
                }
                currentOffset
            }
            else -> error("Unknown thrift type $type at $offset")
        }

    private fun fStr(
        id: Int,
        value: String,
    ): ByteArray = fBin(id, value.encodeToByteArray())

    private fun fBin(
        id: Int,
        value: ByteArray,
    ): ByteArray = fieldHeader(T_STRING, id) + i32be(value.size) + value

    private fun fBool(
        id: Int,
        value: Boolean,
    ): ByteArray = fieldHeader(T_BOOL, id) + byteArrayOf((if (value) 1 else 0).toByte())

    private fun fI32(
        id: Int,
        value: Int,
    ): ByteArray = fieldHeader(T_I32, id) + i32be(value)

    private fun fI64(
        id: Int,
        value: Long,
    ): ByteArray = fieldHeader(T_I64, id) + i64be(value)

    private fun fStruct(
        id: Int,
        bytes: ByteArray,
    ): ByteArray = fieldHeader(T_STRUCT, id) + bytes

    private fun listOfStructs(
        id: Int,
        structs: List<ByteArray>,
    ): ByteArray =
        fieldHeader(T_LIST, id) +
            byteArrayOf(T_STRUCT.toByte()) +
            i32be(structs.size) +
            structs.fold(ByteArray(0)) { acc, bytes -> acc + bytes }

    private fun listOfStrings(
        id: Int,
        values: List<String>,
    ): ByteArray =
        fieldHeader(T_LIST, id) +
            byteArrayOf(T_STRING.toByte()) +
            i32be(values.size) +
            values.fold(ByteArray(0)) { acc, value ->
                val bytes = value.encodeToByteArray()
                acc + i32be(bytes.size) + bytes
            }

    private fun fieldHeader(
        type: Int,
        id: Int,
    ): ByteArray = byteArrayOf(type.toByte(), (id ushr 8).toByte(), id.toByte())

    private fun i32be(value: Int): ByteArray =
        byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )

    private fun i64be(value: Long): ByteArray =
        ByteArray(8) { index ->
            (value ushr ((7 - index) * 8)).toByte()
        }

    private fun readI32(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        (bytes[offset].u() shl 24) or
            (bytes[offset + 1].u() shl 16) or
            (bytes[offset + 2].u() shl 8) or
            bytes[offset + 3].u()

    private fun readI64(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        var value = 0L
        repeat(8) { index ->
            value = (value shl 8) or bytes[offset + index].u().toLong()
        }
        return value
    }

    private fun Byte.u(): Int = toInt() and 0xff

    private const val T_STOP = 0
    private const val T_BOOL = 2
    private const val T_I32 = 8
    private const val T_I64 = 10
    private const val T_STRING = 11
    private const val T_STRUCT = 12
    private const val T_LIST = 15
    private val STOP = byteArrayOf(T_STOP.toByte())
}

internal enum class ScreenCaptureType(
    val value: Int,
) {
    Unknown(0),
    Screenshot(1),
    Recording(2),
}

internal fun XChatThriftStruct.struct(id: Int): XChatThriftStruct? =
    (this[id] as? XChatThriftValue.Struct)?.fields

internal fun XChatThriftStruct.list(id: Int): List<XChatThriftValue>? =
    (this[id] as? XChatThriftValue.ListValue)?.values

internal fun XChatThriftStruct.binary(id: Int): ByteArray? =
    (this[id] as? XChatThriftValue.Binary)?.bytes

internal fun XChatThriftStruct.string(id: Int): String? = binary(id)?.decodeToString()

internal fun XChatThriftStruct.long(id: Int): Long? = (this[id] as? XChatThriftValue.I64)?.value

internal fun XChatThriftStruct.int(id: Int): Int? = (this[id] as? XChatThriftValue.I32)?.value

internal fun XChatThriftStruct.boolean(id: Int): Boolean? = (this[id] as? XChatThriftValue.Bool)?.value

internal fun XChatThriftValue.asStruct(): XChatThriftStruct? = (this as? XChatThriftValue.Struct)?.fields
