package dev.dimension.flare.data.network.xqt.emusks

internal enum class EmusksHttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
}

internal data class EmusksGraphqlOperation(
    val method: EmusksHttpMethod,
    val queryId: String,
)

internal data class EmusksRestOperation(
    val method: EmusksHttpMethod,
    val url: String,
)

internal object EmusksGraphqlOperations {
    private val operations =
        mapOf(
            "AddParticipantsMutation" to EmusksGraphqlOperation(EmusksHttpMethod.POST, "oBwyQ0_xVbAQ8FAyG0pCRA"),
            "DMConversationSearchTabGroupsQuery" to
                EmusksGraphqlOperation(EmusksHttpMethod.GET, "8D8KoSq5q9d5Su3emu2dwg"),
            "DMConversationSearchTabPeopleQuery" to
                EmusksGraphqlOperation(EmusksHttpMethod.GET, "qno3lU4_eSHtSFoWQUhEag"),
            "DMMessageDeleteMutation" to EmusksGraphqlOperation(EmusksHttpMethod.POST, "BJ6DtxA2llfjnRoRjaiIiw"),
            "DMMessageSearchTabQuery" to EmusksGraphqlOperation(EmusksHttpMethod.GET, "QUobOGFxSYwNxfh2zCpVGA"),
            "DMPinnedInboxAppend_Mutation" to
                EmusksGraphqlOperation(EmusksHttpMethod.POST, "o0aymgGiJY-53Y52YSUGVA"),
            "DMPinnedInboxDelete_Mutation" to
                EmusksGraphqlOperation(EmusksHttpMethod.POST, "_TQxP2Rb0expwVP9ktGrTQ"),
            "DMPinnedInboxQuery" to EmusksGraphqlOperation(EmusksHttpMethod.GET, "_gBQBgClVuMQb8efxWkbbQ"),
            "DmAllSearchSlice" to EmusksGraphqlOperation(EmusksHttpMethod.GET, "nIz5WMsrpV7s0uDu-gfOVw"),
            "dmBlockUser" to EmusksGraphqlOperation(EmusksHttpMethod.POST, "IYw9u1KEhrS-t-BXsau4Uw"),
            "dmUnblockUser" to EmusksGraphqlOperation(EmusksHttpMethod.POST, "Krbs6Nak_o7liWQwfV1jOQ"),
            "useDMReactionMutationAddMutation" to
                EmusksGraphqlOperation(EmusksHttpMethod.POST, "VyDyV9pC2oZEj6g52hgnhA"),
            "useDMReactionMutationRemoveMutation" to
                EmusksGraphqlOperation(EmusksHttpMethod.POST, "bV_Nim3RYHsaJwMkTXJ6ew"),
        )

    operator fun get(name: String): EmusksGraphqlOperation? = operations[name]
}

internal object EmusksV11Operations {
    private val operations =
        mapOf(
            "dm/conversation" to
                EmusksRestOperation(EmusksHttpMethod.GET, "https://api.x.com/1.1/dm/conversation.json"),
            "dm/inbox_initial_state" to
                EmusksRestOperation(EmusksHttpMethod.GET, "https://api.x.com/1.1/dm/inbox_initial_state.json"),
            "dm/new2" to
                EmusksRestOperation(EmusksHttpMethod.POST, "https://api.x.com/1.1/dm/new2.json"),
            "dm/permissions" to
                EmusksRestOperation(EmusksHttpMethod.GET, "https://api.x.com/1.1/dm/permissions.json"),
            "dm/update_last_seen_event_id" to
                EmusksRestOperation(EmusksHttpMethod.POST, "https://api.x.com/1.1/dm/update_last_seen_event_id.json"),
            "dm/user_updates" to
                EmusksRestOperation(EmusksHttpMethod.GET, "https://api.x.com/1.1/dm/user_updates.json"),
            "dm/welcome_messages/add_to_conversation" to
                EmusksRestOperation(
                    EmusksHttpMethod.POST,
                    "https://api.x.com/1.1/dm/welcome_messages/add_to_conversation.json",
                ),
        )

    operator fun get(name: String): EmusksRestOperation? = operations[name]
}

internal object EmusksV2Operations {
    private val operations = emptyMap<String, EmusksRestOperation>()

    operator fun get(name: String): EmusksRestOperation? = operations[name]
}
