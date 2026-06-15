package dev.dimension.flare.data.network.xqt.xchat

internal data class XChatOperation(
    val id: String,
    val document: String,
)

internal object XChatOperations {
    private const val TOKEN_MAP_FRAGMENT =
        "fragment TokenMapFragment on KeyStoreTokenMap { __typename realm_state realm_state_string max_guess_count recover_threshold register_threshold token_map { __typename key value { __typename token address public_key } } key_store_token_map_json }"

    private val operations =
        mapOf(
            "GetPublicKeys" to
                XChatOperation(
                    id = "GJQbOZALDO5D3Zp2IZhH6w",
                    document =
                        """
                        query GetPublicKeys(
                          ${'$'}ids: [NumericString!]!,
                          ${'$'}include_juicebox_tokens: Boolean = false
                        ) {
                          user_results_by_rest_ids(rest_ids: ${'$'}ids, safety_level: XChat) {
                            __typename
                            ... on UserResults {
                              rest_id
                              id
                              result {
                                __typename
                                ... on User {
                                  chat_permissions {
                                    __typename
                                    can_dm
                                    can_dm_on_xchat
                                    dm_blocking
                                    passes_premium_check
                                  }
                                  ...UserPublicKeysFragment
                                }
                              }
                            }
                          }
                        }
                        $TOKEN_MAP_FRAGMENT
                        fragment UserPublicKeysFragment on User {
                          __typename
                          get_public_keys {
                            __typename
                            ... on GetPublicKeysResult {
                              public_keys_with_token_map {
                                __typename
                                public_key_with_metadata {
                                  __typename
                                  public_key {
                                    __typename
                                    public_key
                                    signing_public_key
                                    identity_public_key_signature
                                    registration_method
                                  }
                                  version
                                }
                                token_map @include(if: ${'$'}include_juicebox_tokens) {
                                  __typename
                                  ...TokenMapFragment
                                }
                                target_token_map @include(if: ${'$'}include_juicebox_tokens) {
                                  __typename
                                  ...TokenMapFragment
                                }
                              }
                              is_managed_pin_user
                              registration_method
                            }
                            ... on GetPublicKeysError {
                              error
                            }
                          }
                        }
                        """.trimIndent(),
                ),
            "AddEncryptedConversationKeysMutation" to
                XChatOperation(
                    id = "4V1KC8ue2tHHvRuIzeczdg",
                    document =
                        """
                        mutation AddEncryptedConversationKeysMutation(
                          ${'$'}conversation_id: String!,
                          ${'$'}conversation_key_version: NumericString!,
                          ${'$'}conversation_participant_keys: [ApiConversationParticipantKeyInput!]!,
                          ${'$'}base64_encoded_key_rotation: String,
                          ${'$'}action_signatures: [ActionSignatureInput!],
                          ${'$'}ttl_msec: NumericString
                        ) {
                          xchat_add_encrypted_conversation_key(
                            conversation_id: ${'$'}conversation_id,
                            conversation_key_version: ${'$'}conversation_key_version,
                            conversation_participant_keys: ${'$'}conversation_participant_keys,
                            base64_encoded_key_rotation: ${'$'}base64_encoded_key_rotation,
                            safety_level: XChat,
                            action_signatures: ${'$'}action_signatures,
                            ttl_msec: ${'$'}ttl_msec
                          ) {
                            __typename
                            error_code
                            conversation_key_change_sequence_id
                          }
                        }
                        """.trimIndent(),
                ),
            "GenerateXChatTokenMutation" to
                XChatOperation(
                    id = "Qh3fZRjPPtPoHYR_2sCZsA",
                    document =
                        """
                        mutation GenerateXChatTokenMutation {
                          user_get_x_chat_auth_token(safety_level: XChat) {
                            __typename
                            error_code
                            token
                          }
                        }
                        """.trimIndent(),
                ),
            "DmAvPermissionsQuery" to
                XChatOperation(
                    id = "kfX5AHDKZrivyHwCaz68mQ",
                    document =
                        """
                        query DmAvPermissionsQuery(${'$'}recipient_ids: [NumericString!]!) {
                          get_av_permissions(
                            recipient_ids: ${'$'}recipient_ids,
                            safety_level: DirectMessagesConversationTimeline
                          ) {
                            __typename
                            result {
                              __typename
                              can_dm
                              error_code
                            }
                          }
                        }
                        """.trimIndent(),
                ),
            "SendMessageCreateMutation" to
                XChatOperation(
                    id = "TWRPP7gnKwV_R8-tE-Dd3Q",
                    document =
                        """
                        mutation SendMessageCreateMutation(
                          ${'$'}conversation_id: String!,
                          ${'$'}message_id: String!,
                          ${'$'}conversation_token: String,
                          ${'$'}encoded_message_create_event: String,
                          ${'$'}encoded_message_event_signature: String
                        ) {
                          xchat_send_create_message_event(
                            conversation_id: ${'$'}conversation_id,
                            conversation_token: ${'$'}conversation_token,
                            encoded_message_create_event: ${'$'}encoded_message_create_event,
                            encoded_message_event_signature: ${'$'}encoded_message_event_signature,
                            message_id: ${'$'}message_id,
                            safety_level: XChat
                          ) {
                            __typename
                            encoded_message_event
                          }
                        }
                        """.trimIndent(),
                ),
            "DeleteMessageMutation" to
                XChatOperation(
                    id = "4gsDQKEmYkOtvsSIpHXdQA",
                    document =
                        """
                        mutation DeleteMessageMutation(
                          ${'$'}sequence_ids: [String!],
                          ${'$'}conversation_id: String,
                          ${'$'}delete_message_action: DeleteMessageActionInput,
                          ${'$'}action_signatures: [ActionSignatureInput!]
                        ) {
                          xchat_delete_messages(
                            safety_level: XChat,
                            sequence_ids: ${'$'}sequence_ids,
                            conversation_id: ${'$'}conversation_id,
                            delete_message_action: ${'$'}delete_message_action,
                            action_signatures: ${'$'}action_signatures
                          ) {
                            __typename
                            error_code
                          }
                        }
                        """.trimIndent(),
                ),
            "GetInitialXChatPageQuery" to
                XChatOperation(
                    id = "XbDtnn4iqMRO13uK4cgqCA",
                    document =
                        """
                        query GetInitialXChatPageQuery(
                          ${'$'}max_local_sequence_id: NumericString,
                          ${'$'}query_settings: XChatPageQuerySettingsInput,
                          ${'$'}message_pull_version: Int,
                          ${'$'}include_participants_results_for_inbox_preview: Boolean! = true
                        ) {
                          get_initial_chat_page(
                            max_local_sequence_id: ${'$'}max_local_sequence_id,
                            query_settings: ${'$'}query_settings,
                            message_pull_version: ${'$'}message_pull_version,
                            safety_level: XChat
                          ) {
                            __typename
                            inboxCursor: cursor {
                              __typename
                              ... on XChatGetInboxPageContinueCursor {
                                cursor_id
                                graph_snapshot_id
                                graph_snapshot_restarted
                              }
                              ... on XChatGetInboxPageEndCursor {
                                inbox_exhausted
                                graph_snapshot_id
                              }
                            }
                            items {
                              __typename
                              latest_message_events
                              latest_notifiable_message_create_event
                              latest_non_notifiable_message_create_event
                              latest_conversation_key_change_events
                              latest_message_sequence_id
                              latest_read_events_per_participant {
                                __typename
                                participant_id_results {
                                  __typename
                                  rest_id
                                }
                                latest_mark_conversation_read_event
                              }
                              conversation_detail {
                                __typename
                                ... on XChatDirectConversationDetail {
                                  conversation_id
                                  is_muted
                                  participants_results @include(if: ${'$'}include_participants_results_for_inbox_preview) {
                                    __typename
                                    ...XChatUserResultFragment
                                  }
                                }
                                ... on XChatGroupConversationDetail {
                                  conversation_id
                                  is_muted
                                  group_metadata {
                                    __typename
                                    group_name
                                    group_avatar_url
                                  }
                                  group_members_results {
                                    __typename
                                    rest_id
                                  }
                                  participants_results @include(if: ${'$'}include_participants_results_for_inbox_preview) {
                                    __typename
                                    ...XChatUserResultFragment
                                  }
                                }
                              }
                            }
                            encoded_message_events
                            message_requests_count
                            message_pull_version
                            max_user_sequence_id
                          }
                        }
                        fragment XChatUserResultFragment on UserResults {
                          __typename
                          rest_id
                          result {
                            __typename
                            ... on User {
                              rest_id
                              avatar {
                                __typename
                                image_url
                              }
                              core {
                                __typename
                                name
                                screen_name
                                created_at_ms
                              }
                            }
                          }
                        }
                        """.trimIndent(),
                ),
            "GetConversationPageQuery" to
                XChatOperation(
                    id = "IVlXls9JTnbgQ1gxsGAfJA",
                    document =
                        """
                        query GetConversationPageQuery(
                          ${'$'}conversation_id: String,
                          ${'$'}min_local_sequence_id: NumericString!,
                          ${'$'}min_conversation_key_version: NumericString!,
                          ${'$'}query_settings: XChatPageQuerySettingsInput
                        ) {
                          get_conversation_page(
                            conversation_id: ${'$'}conversation_id,
                            min_local_sequence_id: ${'$'}min_local_sequence_id,
                            min_conversation_key_version: ${'$'}min_conversation_key_version,
                            query_settings: ${'$'}query_settings,
                            safety_level: XChat
                          ) {
                            __typename
                            encoded_message_events
                            missing_conversation_key_change_events
                            has_more
                          }
                        }
                        """.trimIndent(),
                ),
        )

    operator fun get(name: String): XChatOperation? = operations[name]
}
