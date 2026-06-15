package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface DirectMessageLoader {
    public val platformType: PlatformType

    public val runtimeTransformer: Flow<DirectMessageRuntimeTransformer>
        get() = flowOf(DirectMessageRuntimeTransformer())

    public val pinCodeStatus: Flow<DirectMessagePinCodeStatus>
        get() = flowOf(DirectMessagePinCodeStatus.NotRequired)

    public suspend fun submitPinCode(pinCode: String): DirectMessagePinCodeStatus = DirectMessagePinCodeStatus.NotRequired

    public suspend fun loadRooms(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMRoom>

    public suspend fun loadMessages(
        roomKey: MicroBlogKey,
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMItem>

    public suspend fun fetchRoomInfo(roomKey: MicroBlogKey): UiDMRoom

    public suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
    ): UiDMItem

    public suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    )

    public suspend fun fetchNewMessages(
        roomKey: MicroBlogKey,
        cursor: String?,
    ): DirectMessageDelta

    public suspend fun leaveRoom(roomKey: MicroBlogKey)

    public fun createRoom(userKey: MicroBlogKey): Flow<UiState<UiDMRoom>>

    public suspend fun canSend(userKey: MicroBlogKey): Boolean

    public suspend fun loadBadgeCount(): Int
}

@HiddenFromObjC
public data class DirectMessageDelta(
    val messages: List<UiDMItem> = emptyList(),
    val deletedMessageKeys: List<MicroBlogKey> = emptyList(),
)

@HiddenFromObjC
public data class DirectMessageRuntimeTransformer(
    val room: (UiDMRoom) -> UiDMRoom = { it },
    val item: (UiDMItem) -> UiDMItem = { it },
)

@HiddenFromObjC
public sealed interface DirectMessagePinCodeStatus {
    public data object NotRequired : DirectMessagePinCodeStatus

    public data object Required : DirectMessagePinCodeStatus

    public data object Verifying : DirectMessagePinCodeStatus

    public data object Verified : DirectMessagePinCodeStatus

    public data class Error(
        val message: String?,
    ) : DirectMessagePinCodeStatus
}
