package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.DMConversationSearchTabGroupsQueryResponse
import dev.dimension.flare.data.network.xqt.model.DMConversationSearchTabPeopleQueryResponse
import dev.dimension.flare.data.network.xqt.model.DMInboxPinnedInboxQueryResponse
import dev.dimension.flare.data.network.xqt.model.DMMessageSearchTabQueryResponse
import dev.dimension.flare.data.network.xqt.model.DmAllSearchSliceResponse

internal interface DmApi {
    /**
     * GET graphql/{pathQueryId}/DMConversationSearchTabGroupsQuery
     *
     * DM Conversation SearchTab Groups Query
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "8D8KoSq5q9d5Su3emu2dwg")
     * @param variables
     * @return [DMConversationSearchTabGroupsQueryResponse]
     */
    @GET("graphql/{pathQueryId}/DMConversationSearchTabGroupsQuery")
    public suspend fun getDMConversationSearchTabGroupsQuery(
        @Path("pathQueryId") pathQueryId: kotlin.String = "8D8KoSq5q9d5Su3emu2dwg",
        @Query("variables") variables: kotlin.String,
    ): Response<DMConversationSearchTabGroupsQueryResponse>

    /**
     * GET graphql/{pathQueryId}/DMConversationSearchTabPeopleQuery
     *
     * DM Conversation SearchTab PeopleQuery
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "qno3lU4_eSHtSFoWQUhEag")
     * @param variables
     * @return [DMConversationSearchTabPeopleQueryResponse]
     */
    @GET("graphql/{pathQueryId}/DMConversationSearchTabPeopleQuery")
    public suspend fun getDMConversationSearchTabPeopleQuery(
        @Path("pathQueryId") pathQueryId: kotlin.String = "qno3lU4_eSHtSFoWQUhEag",
        @Query("variables") variables: kotlin.String,
    ): Response<DMConversationSearchTabPeopleQueryResponse>

    /**
     * GET graphql/{pathQueryId}/DMMessageSearchTabQuery
     *
     * DM Message Search Tab Query
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "QUobOGFxSYwNxfh2zCpVGA")
     * @param variables
     * @param features
     * @return [DMMessageSearchTabQueryResponse]
     */
    @GET("graphql/{pathQueryId}/DMMessageSearchTabQuery")
    public suspend fun getDMMessageSearchTabQuery(
        @Path("pathQueryId") pathQueryId: kotlin.String = "QUobOGFxSYwNxfh2zCpVGA",
        @Query("variables") variables: kotlin.String,
        @Query("features") features: kotlin.String,
    ): Response<DMMessageSearchTabQueryResponse>

    /**
     * GET graphql/{pathQueryId}/DMPinnedInboxQuery
     *
     * get DM Pinned Inbox Query
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "_gBQBgClVuMQb8efxWkbbQ")
     * @param variables
     * @return [DMInboxPinnedInboxQueryResponse]
     */
    @GET("graphql/{pathQueryId}/DMPinnedInboxQuery")
    public suspend fun getDMPinnedInboxQuery(
        @Path("pathQueryId") pathQueryId: kotlin.String = "_gBQBgClVuMQb8efxWkbbQ",
        @Query("variables") variables: kotlin.String = "{\"label\":\"Pinned\"}",
    ): DMInboxPinnedInboxQueryResponse

    /**
     * GET graphql/{pathQueryId}/DmAllSearchSlice
     *
     * get Dm All SearchSlice
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "hNFpW5uN1FOZBE8u1HJicw")
     * @param variables
     * @param features
     * @return [DmAllSearchSliceResponse]
     */
    @GET("graphql/{pathQueryId}/DmAllSearchSlice")
    public suspend fun getDmAllSearchSlice(
        @Path("pathQueryId") pathQueryId: kotlin.String = "hNFpW5uN1FOZBE8u1HJicw",
        @Query("variables") variables: kotlin.String,
        @Query("features") features: kotlin.String,
    ): Response<DmAllSearchSliceResponse>
}
