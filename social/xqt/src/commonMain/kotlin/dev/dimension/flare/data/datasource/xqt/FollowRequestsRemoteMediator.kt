package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQT_JSON
import dev.dimension.flare.data.network.xqt.api.FollowRequestsApi
import dev.dimension.flare.data.network.xqt.api.checkedFollowRequestBody
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserLegacy
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

internal class FollowRequestsRemoteMediator(
    private val service: FollowRequestsApi,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "follow_requests_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val cursor =
            when (request) {
                PagingRequest.Refresh -> "-1"
                is PagingRequest.Append -> request.nextKey
                is PagingRequest.Prepend -> return PagingResult(endOfPaginationReached = true)
            }
        val page =
            XQT_JSON.decodeFromJsonElement<PendingFollowRequests>(
                service.incomingFollowRequests(cursor, pageSize.coerceIn(1, 100)).checkedFollowRequestBody(),
            )
        val users =
            page.ids
                .distinct()
                .chunked(100)
                .flatMap { ids ->
                    service.followRequestUsers(ids.joinToString(",")).checkedFollowRequestBody().jsonArray.map { element ->
                        val user = element.jsonObject
                        User(
                            restId = user.getValue("id_str").jsonPrimitive.content,
                            legacy = XQT_JSON.decodeFromJsonElement<UserLegacy>(user),
                            isBlueVerified = user["is_blue_verified"]?.jsonPrimitive?.booleanOrNull == true,
                        )
                    }
                }.associateBy { it.restId }
        return PagingResult(
            data = page.ids.distinct().mapNotNull { users[it]?.renderFollowRequest() },
            nextKey = page.nextCursor.takeUnless { it == "0" || it == cursor || it.isEmpty() },
        )
    }

    private fun User.renderFollowRequest(): UiTimelineV2.User {
        val user = render(accountKey)
        val key = MicroBlogKey("follow_request_${accountKey.id}_$restId", accountKey.host)
        return UiTimelineV2.User(
            value = user,
            statusKey = key,
            accountType = AccountType.Specific(accountKey),
            createdAt = Instant.DISTANT_PAST.toUi(),
            button =
                persistentListOf(
                    ActionMenu.Item(
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.AcceptFollowRequest),
                        clickEvent = ClickEvent.event(accountKey, PostEvent.XQT.AcceptFollowRequest(key, user.key)),
                        color = ActionMenu.Item.Color.PrimaryColor,
                        icon = UiIcon.Check,
                    ),
                    ActionMenu.Item(
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.RejectFollowRequest),
                        clickEvent = ClickEvent.event(accountKey, PostEvent.XQT.RejectFollowRequest(key, user.key)),
                        color = ActionMenu.Item.Color.Red,
                    ),
                ),
        )
    }
}

@Serializable
private data class PendingFollowRequests(
    val ids: List<String>,
    @SerialName("next_cursor_str") val nextCursor: String,
)
