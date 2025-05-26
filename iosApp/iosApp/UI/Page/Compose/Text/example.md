1. 如果用raw，则需要拆分出markdown 的内容。链接 ，另外 accountKey是否在用？
    # 只需要文字
    @   ProfileWithUserNameScreen(
                    accountType: accountType, // 是需要accountKey的
                    userName: userName,
                    host: host,
                    toProfileMedia: { _ in  }
        )
2. 如果用markdown
    1.  EmojiText 是否支持样式等问题？链接是否支持长按
        支持linkColor ，但是 长按就不支持了

3. other？ need support mastdon 的emoji    
  
   UiRichText(markdown=Chudai<br/>[@SexytoBaby](flare://ProfileWithNameAndHost/SexytoBaby/twitter.com?accountKey=426425493@twitter.com)         <br/>[@SexytoLady](flare://ProfileWithNameAndHost/SexytoLady/twitter.com?accountKey=426425493@twitter.com) <br/>[@SexytoDoll](flare://ProfileWithNameAndHost/SexytoDoll/twitter.com?accountKey=426425493@twitter.com) <br/>[@SexytoGirl](flare://ProfileWithNameAndHost/SexytoGirl/twitter.com?accountKey=426425493@twitter.com)
   ,
   raw=Chudai @SexytoBaby @SexytoLady @SexytoDoll @SexytoGirl)
   
  **********************************************************************************************************
 
   {
  "entryId": "tweet-1926829391503609941",
"sortIndex": "1833187323711808681",
"content": {
    "entryType": "TimelineTimelineItem",
    "__typename": "TimelineTimelineItem",
    "itemContent": {
        "itemType": "TimelineTweet",
        "__typename": "TimelineTweet",
        "tweet_results": {
            "result": {
                "__typename": "Tweet",
                "rest_id": "1926829391503609941",
                "core": {
                    "user_results": {
                        "result": {
                            "__typename": "User",
                            "id": "VXNlcjoxNzU3NDk1ODQ2NzI2MTY4NTc2",
                            "rest_id": "1757495846726168576",
                            "affiliates_highlighted_label": {},
                            "avatar": {
                                "image_url": "https://pbs.twimg.com/profile_images/1758176315939700736/aPP0uGtf_normal.jpg"
                            },
                            "core": {
                                "created_at": "Tue Feb 13 20:04:09 +0000 2024",
                                "name": "Terica Jovan\uD83D\uDC45",
                                "screen_name": "TJovan80819"
                            },
                            "dm_permissions": {
                                "can_dm": true
                            },
                            "has_graduated_access": true,
                            "is_blue_verified": true,
                            "legacy": {
                                "default_profile": true,
                                "default_profile_image": false,
                                "description": "sexiness」「erotism」「amorousness」「amativeness」\uD83E\uDEE6\uD83E\uDEE6\uD83E\uDEE6\uD83E\uDEE6",
                                "entities": {
                                    "description": {
                                        "urls": []
                                    }
                                },
                                "fast_followers_count": 0,
                                "favourites_count": 1,
                                "followers_count": 7800,
                                "friends_count": 1,
                                "has_custom_timelines": false,
                                "is_translator": false,
                                "listed_count": 22,
                                "media_count": 12,
                                "normal_followers_count": 7800,
                                "pinned_tweet_ids_str": [],
                                "possibly_sensitive": true,
                                "profile_banner_url": "https://pbs.twimg.com/profile_banners/1757495846726168576/1744352943",
                                "profile_interstitial_type": "",
                                "statuses_count": 46,
                                "translator_type": "none",
                                "want_retweets": false,
                                "withheld_in_countries": []
                            },
                            "location": {
                                "location": ""
                            },
                            "media_permissions": {
                                "can_media_tag": true
                            },
                            "parody_commentary_fan_label": "None",
                            "profile_image_shape": "Circle",
                            "privacy": {
                                "protected": false
                            },
                            "relationship_perspectives": {
                                "following": false
                            },
                            "tipjar_settings": {},
                            "verification": {
                                "verified": false
                            }
                        }
                    }
                },
                "card": {
                    "rest_id": "card://1926255425994797056",
                    "legacy": {
                        "binding_values": [
                            {
                                "key": "unified_card",
                                "value": {
                                    "string_value": "{\"type\":\"video_website\",\"component_objects\":{\"details_1\":{\"type\":\"details\",\"data\":{\"title\":{\"content\":\"Chudai\",\"is_rtl\":false},\"subtitle\":{\"content\":\"nr77.top\",\"is_rtl\":false},\"destination\":\"override_browser_with_docked_media_1\"}},\"media_1\":{\"type\":\"media\",\"data\":{\"id\":\"13_1925641018847703040\",\"destination\":\"browser_with_docked_media_1\"}}},\"destination_objects\":{\"browser_with_docked_media_1\":{\"type\":\"browser_with_docked_media\",\"data\":{\"url_data\":{\"url\":\"https://xhkk.nr77.top\",\"vanity\":\"nr77.top\"},\"media_id\":\"13_1925641018847703040\"}},\"override_browser_with_docked_media_1\":{\"type\":\"browser\",\"data\":{\"url_data\":{\"url\":\"https://xhkk.nr77.top\",\"vanity\":\"nr77.top\"}}}},\"components\":[\"media_1\",\"details_1\"],\"media_entities\":{\"13_1925641018847703040\":{\"id\":1925641018847703040,\"id_str\":\"1925641018847703040\",\"indices\":[0,0],\"media_url\":\"\",\"media_url_https\":\"https://pbs.twimg.com/amplify_video_thumb/1925641018847703040/img/xHbWrhHX2XE7UWgV.jpg\",\"url\":\"\",\"display_url\":\"\",\"expanded_url\":\"\",\"type\":\"video\",\"original_info\":{\"width\":1280,\"height\":720},\"sizes\":{\"large\":{\"w\":1280,\"h\":720,\"resize\":\"fit\"},\"medium\":{\"w\":1200,\"h\":675,\"resize\":\"fit\"},\"thumb\":{\"w\":150,\"h\":150,\"resize\":\"crop\"},\"small\":{\"w\":680,\"h\":383,\"resize\":\"fit\"}},\"source_user_id\":1757495846726168576,\"source_user_id_str\":\"1757495846726168576\",\"video_info\":{\"aspect_ratio\":[16,9],\"duration_millis\":640766,\"variants\":[{\"bitrate\":2176000,\"content_type\":\"video/mp4\",\"url\":\"https://video.twimg.com/amplify_video/1925641018847703040/vid/avc1/1280x720/4BS44k2vmRfbI-HY.mp4?tag=16\"},{\"content_type\":\"application/x-mpegURL\",\"url\":\"https://video.twimg.com/amplify_video/1925641018847703040/pl/yRQf32Ah6ECZT8FB.m3u8?tag=16\"},{\"bitrate\":288000,\"content_type\":\"video/mp4\",\"url\":\"https://video.twimg.com/amplify_video/1925641018847703040/vid/avc1/480x270/M6-ZODCrKZIf46x3.mp4?tag=16\"},{\"bitrate\":832000,\"content_type\":\"video/mp4\",\"url\":\"https://video.twimg.com/amplify_video/1925641018847703040/vid/avc1/640x360/MIoJIjYnv3V61RzX.mp4?tag=16\"}]},\"media_key\":\"13_1925641018847703040\",\"ext\":{\"mediaColor\":{\"r\":{\"ok\":{\"palette\":[{\"rgb\":{\"red\":183,\"green\":125,\"blue\":128},\"percentage\":39.98},{\"rgb\":{\"red\":72,\"green\":60,\"blue\":65},\"percentage\":13.08},{\"rgb\":{\"red\":177,\"green\":164,\"blue\":195},\"percentage\":10.97},{\"rgb\":{\"red\":122,\"green\":67,\"blue\":73},\"percentage\":7.11},{\"rgb\":{\"red\":238,\"green\":154,\"blue\":197},\"percentage\":6.34}]}},\"ttl\":-1}}}}}",
                                    "type": "STRING"
                                }
                            },
                            {
                                "key": "card_url",
                                "value": {
                                    "scribe_key": "card_url",
                                    "string_value": "https://twitter.com",
                                    "type": "STRING"
                                }
                            }
                        ],
                        "card_platform": {
                            "platform": {
                                "audience": {
                                    "name": "production"
                                },
                                "device": {
                                    "name": "Swift",
                                    "version": "12"
                                }
                            }
                        },
                        "name": "unified_card",
                        "url": "card://1926255425994797056",
                        "user_refs_results": []
                    }
                },
                "unmention_data": {},
                "edit_control": {
                    "edit_tweet_ids": [
                        "1926829391503609941"
                    ],
                    "editable_until_msecs": "1748230500000",
                    "is_edit_eligible": true,
                    "edits_remaining": "5"
                },
                "is_translatable": false,
                "views": {
                    "count": "27771",
                    "state": "EnabledWithCount"
                },
                "source": "<a href=\"https://twitter.com\" rel=\"nofollow\">Twitter for Advertisers</a>",
                "legacy": {
                    "bookmark_count": 209,
                    "bookmarked": true,
                    "created_at": "Mon May 26 02:35:00 +0000 2025",
                    "conversation_id_str": "1926829391503609941",
                    "display_text_range": [
                        0,
                        54
                    ],
                    "entities": {
                        "hashtags": [],
                        "symbols": [],
                        "timestamps": [],
                        "urls": [],
                        "user_mentions": [
                            {
                                "id_str": "1526018966707179520",
                                "name": "日理万姬\uD83D\uDC95",
                                "screen_name": "SexytoBaby",
                                "indices": [
                                    7,
                                    18
                                ]
                            },
                            {
                                "id_str": "1651581863851610112",
                                "name": "金屋藏娇\uD83D\uDC95",
                                "screen_name": "SexytoLady",
                                "indices": [
                                    19,
                                    30
                                ]
                            },
                            {
                                "id_str": "1692492982187114497",
                                "name": "佳丽三千\uD83D\uDC95",
                                "screen_name": "SexytoDoll",
                                "indices": [
                                    31,
                                    42
                                ]
                            },
                            {
                                "id_str": "1757780025032802304",
                                "name": "千娇百媚\uD83D\uDC95",
                                "screen_name": "SexytoGirl",
                                "indices": [
                                    43,
                                    54
                                ]
                            }
                        ]
                    },
                    "favorite_count": 492,
                    "favorited": false,
                    "full_text": "Chudai\n@SexytoBaby\n@SexytoLady\n@SexytoDoll\n@SexytoGirl",
                    "is_quote_status": false,
                    "lang": "hi",
                    "quote_count": 1,
                    "reply_count": 0,
                    "retweet_count": 105,
                    "retweeted": false,
                    "user_id_str": "1757495846726168576",
                    "id_str": "1926829391503609941"
                }
            }
        },
        "tweetDisplayType": "Tweet"
    }
}
} 

