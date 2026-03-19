package dev.dimension.flare.data.datasource.bluesky

import app.bsky.actor.GetProfileQueryParams
import app.bsky.graph.GetListQueryParams
import app.bsky.graph.GetListsQueryParams
import app.bsky.graph.Listitem
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.ListRecordsQueryParams
import dev.dimension.flare.data.datasource.microblog.loader.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import kotlin.time.Clock

internal class BlueskyListMemberLoader(
    private val getService: suspend () -> BlueskyService,
    private val accountKey: MicroBlogKey,
) : ListMemberLoader {
    override suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listId: String,
    ): PagingResult<UiProfile> {
        val service = getService()
        val cursor =
            when (request) {
                is PagingRequest.Append -> request.nextKey
                is PagingRequest.Refresh -> null
                is PagingRequest.Prepend -> return PagingResult()
            }
        val response =
            service
                .getList(
                    params =
                        GetListQueryParams(
                            list = AtUri(listId),
                            cursor = cursor,
                            limit = pageSize.toLong(),
                        ),
                ).requireResponse()
        val users =
            response.items
                .map {
                    it.subject.render(accountKey)
                }
        return PagingResult(
            data = users,
            nextKey = response.cursor,
        )
    }

    override suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ): UiProfile {
        val service = getService()
        val user =
            service
                .getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                .requireResponse()

        service.createRecord(
            CreateRecordRequest(
                repo = Did(did = accountKey.id),
                collection = Nsid("app.bsky.graph.listitem"),
                record =
                    app.bsky.graph
                        .Listitem(
                            list = AtUri(listId),
                            subject = Did(userKey.id),
                            createdAt = Clock.System.now(),
                        ).bskyJson(),
            ),
        )
        return user.render(accountKey)
    }

    override suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        val service = getService()
        var record: com.atproto.repo.ListRecordsRecord? = null
        var cursor: String? = null
        while (record == null) {
            val response =
                service
                    .listRecords(
                        params =
                            ListRecordsQueryParams(
                                repo = Did(did = accountKey.id),
                                collection = Nsid("app.bsky.graph.listitem"),
                                limit = 100,
                                cursor = cursor,
                            ),
                    ).requireResponse()
            if (response.cursor == null || response.records.isEmpty()) {
                break
            }
            cursor = response.cursor
            record =
                response.records
                    .firstOrNull {
                        val item: Listitem = it.value.decodeAs()
                        item.list.atUri == listId && item.subject.did == userKey.id
                    }
        }
        if (record != null) {
            service.deleteRecord(
                DeleteRecordRequest(
                    repo = Did(did = accountKey.id),
                    collection = Nsid("app.bsky.graph.listitem"),
                    rkey = RKey(record.uri.atUri.substringAfterLast('/')),
                ),
            )
        }
    }

    override suspend fun loadUserLists(
        pageSize: Int,
        request: PagingRequest,
        userKey: MicroBlogKey,
    ): PagingResult<UiList> {
        val service = getService()
        if (request is PagingRequest.Prepend) {
            return PagingResult()
        }
        // Bluesky doesn't have an endpoint to get lists a user is in, so we have to iterate through all lists
        // Since we can't easily pagination this, we will load everything at once for the first page
        if (request is PagingRequest.Append) {
            return PagingResult(nextKey = null)
        }

        var cursor: String? = null
        val lists = mutableListOf<UiList.List>()
        val allLists =
            service
                .getLists(
                    params =
                        GetListsQueryParams(
                            actor = Did(did = accountKey.id),
                            limit = 100,
                        ),
                ).requireResponse()
                .lists
                .map {
                    it.render(accountKey)
                }
        while (true) {
            val response =
                service
                    .listRecords(
                        params =
                            ListRecordsQueryParams(
                                repo = Did(did = accountKey.id),
                                collection = Nsid("app.bsky.graph.listitem"),
                                limit = 100,
                                cursor = cursor,
                            ),
                    ).requireResponse()
            lists.addAll(
                response.records
                    .filter {
                        val item: Listitem = it.value.decodeAs()
                        item.subject.did == userKey.id
                    }.mapNotNull {
                        val item: Listitem = it.value.decodeAs()
                        allLists.firstOrNull { list -> list.id == item.list.atUri }
                    },
            )
            cursor = response.cursor
            if (cursor == null) {
                break
            }
        }
        return PagingResult(
            data = lists,
            nextKey = null,
        )
    }
}
