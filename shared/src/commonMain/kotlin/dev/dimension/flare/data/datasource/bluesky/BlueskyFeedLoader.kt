package dev.dimension.flare.data.datasource.bluesky

import app.bsky.actor.PreferencesUnion
import app.bsky.actor.PutPreferencesRequest
import app.bsky.actor.SavedFeed
import app.bsky.actor.SavedFeedType
import app.bsky.feed.GetFeedGeneratorQueryParams
import app.bsky.feed.GetFeedGeneratorsQueryParams
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.StrongRef
import dev.dimension.flare.data.datasource.microblog.list.ListLoader
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.list.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class BlueskyFeedLoader(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
) : ListLoader {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList> {
        val cursor =
            when (request) {
                is PagingRequest.Append -> request.nextKey
                is PagingRequest.Refresh -> null
                is PagingRequest.Prepend -> return PagingResult()
            }

        // Feed loading usually doesn't support pagination in the same way lists do via preferences
        // But we can implement a basic fetch.
        // Bluesky preferences don't really support pagination for saved feeds.
        // We'll load all if cursor is null, otherwise return empty (end of list).
        if (cursor != null) {
            return PagingResult(
                data = persistentListOf(),
                nextKey = null,
            )
        }

        val preferences =
            service
                .getPreferencesForActor()
                .requireResponse()
                .preferences

        val items =
            preferences
                .filterIsInstance<PreferencesUnion.SavedFeedsPrefV2>()
                .firstOrNull()
                ?.value
                ?.items
                ?.filter {
                    it.type == SavedFeedType.Feed
                }.orEmpty()

        val feeds =
            service
                .getFeedGenerators(
                    GetFeedGeneratorsQueryParams(
                        feeds =
                            items
                                .map { AtUri(it.value) }
                                .toImmutableList(),
                    ),
                ).requireResponse()
                .feeds
                .map {
                    it.render(accountKey)
                }.toImmutableList()

        return PagingResult(
            data = feeds,
            nextKey = null,
        )
    }

    override suspend fun info(listId: String): UiList =
        service
            .getFeedGenerator(
                GetFeedGeneratorQueryParams(
                    feed = AtUri(listId),
                ),
            ).requireResponse()
            .view
            .render(accountKey)

    override val supportedMetaData: ImmutableList<ListMetaDataType>
        get() = persistentListOf()

    override suspend fun create(metaData: ListMetaData): UiList = throw UnsupportedOperationException("Create feed is not supported")

    override suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ): UiList = throw UnsupportedOperationException("Update feed is not supported")

    override suspend fun delete(listId: String) {
        val currentPreferences = service.getPreferencesForActor().requireResponse()
        val feedInfo =
            service
                .getFeedGenerator(GetFeedGeneratorQueryParams(feed = AtUri(listId)))
                .requireResponse()
        val newPreferences = currentPreferences.preferences.toMutableList()
        val prefIndex = newPreferences.indexOfFirst { it is PreferencesUnion.SavedFeedsPref }
        if (prefIndex != -1) {
            val pref = newPreferences[prefIndex] as PreferencesUnion.SavedFeedsPref
            val newPref =
                pref.value.copy(
                    saved =
                        pref.value.saved
                            .filterNot { it == feedInfo.view.uri }
                            .toImmutableList(),
                    pinned =
                        pref.value.pinned
                            .filterNot { it == feedInfo.view.uri }
                            .toImmutableList(),
                )
            newPreferences[prefIndex] = PreferencesUnion.SavedFeedsPref(newPref)
        }
        val prefV2Index =
            newPreferences.indexOfFirst { it is PreferencesUnion.SavedFeedsPrefV2 }
        if (prefV2Index != -1) {
            val pref = newPreferences[prefV2Index] as PreferencesUnion.SavedFeedsPrefV2
            val newPref =
                pref.value.copy(
                    items =
                        pref.value.items
                            .filterNot { it.value == feedInfo.view.uri.atUri }
                            .toImmutableList(),
                )
            newPreferences[prefV2Index] = PreferencesUnion.SavedFeedsPrefV2(newPref)
        }
        service.putPreferences(
            request =
                PutPreferencesRequest(
                    preferences = newPreferences.toImmutableList(),
                ),
        )
    }

    suspend fun subscribe(feedUri: String) {
        val currentPreferences = service.getPreferencesForActor().requireResponse()
        val feedInfo =
            service
                .getFeedGenerator(GetFeedGeneratorQueryParams(feed = AtUri(feedUri)))
                .requireResponse()
        val newPreferences = currentPreferences.preferences.toMutableList()
        val prefIndex = newPreferences.indexOfFirst { it is PreferencesUnion.SavedFeedsPref }
        if (prefIndex != -1) {
            val pref = newPreferences[prefIndex] as PreferencesUnion.SavedFeedsPref
            val newPref =
                pref.value.copy(
                    saved = (pref.value.saved + feedInfo.view.uri).toImmutableList(),
                    pinned = (pref.value.pinned + feedInfo.view.uri).toImmutableList(),
                )
            newPreferences[prefIndex] = PreferencesUnion.SavedFeedsPref(newPref)
        }
        val prefV2Index =
            newPreferences.indexOfFirst { it is PreferencesUnion.SavedFeedsPrefV2 }
        if (prefV2Index != -1) {
            val pref = newPreferences[prefV2Index] as PreferencesUnion.SavedFeedsPrefV2
            val newPref =
                pref.value.copy(
                    items =
                        (
                            pref.value.items +
                                SavedFeed(
                                    type = SavedFeedType.Feed,
                                    value = feedInfo.view.uri.atUri,
                                    pinned = true,
                                    id = Uuid.random().toString(),
                                )
                        ).toImmutableList(),
                )
            newPreferences[prefV2Index] = PreferencesUnion.SavedFeedsPrefV2(newPref)
        }

        service.putPreferences(
            request =
                PutPreferencesRequest(
                    preferences = newPreferences.toImmutableList(),
                ),
        )
    }

    suspend fun favourite(feedUri: String) {
        val feedInfo =
            service
                .getFeedGenerator(GetFeedGeneratorQueryParams(feed = AtUri(feedUri)))
                .requireResponse()
        val likedUri =
            feedInfo.view.viewer
                ?.like
                ?.atUri

        // If already liked, technically we shouldn't be here if called correctly, or we treat as idempotent?
        // User request said "favourite" and "unfavourite" separate methods.
        if (likedUri == null) {
            createLikeRecord(cid = feedInfo.view.cid.cid, uri = feedInfo.view.uri.atUri)
        }
    }

    suspend fun unfavourite(feedUri: String) {
        val feedInfo =
            service
                .getFeedGenerator(GetFeedGeneratorQueryParams(feed = AtUri(feedUri)))
                .requireResponse()
        val likedUri =
            feedInfo.view.viewer
                ?.like
                ?.atUri

        if (likedUri != null) {
            deleteLikeRecord(likedUri)
        }
    }

    private suspend fun createLikeRecord(
        cid: String,
        uri: String,
    ) {
        service
            .createRecord(
                CreateRecordRequest(
                    repo = Did(did = accountKey.id),
                    collection = Nsid("app.bsky.feed.like"),
                    record =
                        app.bsky.feed
                            .Like(
                                subject =
                                    StrongRef(
                                        uri = AtUri(uri),
                                        cid = Cid(cid),
                                    ),
                                createdAt = Clock.System.now(),
                            ).bskyJson(),
                ),
            ).requireResponse()
    }

    private suspend fun deleteLikeRecord(likedUri: String) =
        service.deleteRecord(
            DeleteRecordRequest(
                repo = Did(did = accountKey.id),
                collection = Nsid("app.bsky.feed.like"),
                rkey = RKey(likedUri.substringAfterLast('/')),
            ),
        )
}
