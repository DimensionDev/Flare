@file:Suppress("ktlint:standard:max-line-length")

package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.common.JSON
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

// Generated offline with org.rust-nostr:nostr-sdk-kmp-jvm:0.44.8, the pre-migration SDK.
// Public test scalars only (1, repeated 0x11, secp256k1 order - 1); timestamps are fixed at 1700000000.
// Keep these immutable so compatibility is checked independently of Quartz's own encoders/signers.
internal val rustNostrKeyFixtures =
    JSON
        .parseToJsonElement(
            """
            [
              {
                "hex": "0000000000000000000000000000000000000000000000000000000000000001",
                "nsec": "nsec1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqsmhltgl",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "npub": "npub10xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqpkge6d",
                "event": {
                  "id": "4d3421d313ddf62fee56aee85f274177675afa35810ce601ccceaf9d4c59a174",
                  "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                  "created_at": 1700000000,
                  "kind": 1,
                  "tags": [
                    [
                      "content-warning",
                      "warning"
                    ]
                  ],
                  "content": "迁移\n\"quoted\"",
                  "sig": "d9678cba2053b2f52c5abe4d452554a5c21a6148be118ba72e96b1cf500e91f55e86e9ea33a2754a9d1812f956862489c73e6037480128fa7c6e393ed26d1c60"
                }
              },
              {
                "hex": "1111111111111111111111111111111111111111111111111111111111111111",
                "nsec": "nsec1zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygs4rm7hz",
                "pubkey": "4f355bdcb7cc0af728ef3cceb9615d90684bb5b2ca5f859ab0f0b704075871aa",
                "npub": "npub1fu64hh9hes90w2808n8tjc2ajp5yhddjef0ctx4s7zmsgp6cwx4qgy4eg9",
                "event": {
                  "id": "920581aeb92c1cdaf6b4d2ff72ebe04a5c2f15facdce3993c273008dbb6eb6e7",
                  "pubkey": "4f355bdcb7cc0af728ef3cceb9615d90684bb5b2ca5f859ab0f0b704075871aa",
                  "created_at": 1700000000,
                  "kind": 1,
                  "tags": [
                    [
                      "content-warning",
                      "warning"
                    ]
                  ],
                  "content": "迁移\n\"quoted\"",
                  "sig": "fc89ad3fe04279cb9cb0ed7e25df088ddb0bd7b3c61bfd3ec0d74c6769fb2d97ff710ae81a946bd9579cb8562e63f68db648d366e60bfe035a1df91fc4b71945"
                }
              },
              {
                "hex": "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140",
                "nsec": "nsec1lllllllllllllllllllllllll6a2ah8x4ay2qwal6f0ge5pkg9qq7ae6fg",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "npub": "npub10xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqpkge6d",
                "event": {
                  "id": "4d3421d313ddf62fee56aee85f274177675afa35810ce601ccceaf9d4c59a174",
                  "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                  "created_at": 1700000000,
                  "kind": 1,
                  "tags": [
                    [
                      "content-warning",
                      "warning"
                    ]
                  ],
                  "content": "迁移\n\"quoted\"",
                  "sig": "547e2f742a4b2a5e5d80a6d8999423a7b0dc4370eab77fa712137180b15b059d14339a07c9b876e045fd353f4083cec5d5b0a98f4638be0ff87162b0805ec5c9"
                }
              }
            ]
            """.trimIndent(),
        ).jsonArray
        .map { it.jsonObject }

internal val rustNostrEventFixtures =
    JSON
        .parseToJsonElement(
            """
            {
              "target": {
                "id": "32ddc14103c9f2d2957f74f54355e4281381aec64af53bcbde780dae4a58821c",
                "pubkey": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
                "created_at": 1700000000,
                "kind": 1,
                "tags": [],
                "content": "Original Rust note",
                "sig": "73763b4f233db6265fe5234cdc79b97ad4f749e9d8ab6617a0c8eb3bfce093d8f1514fe5ef0531a61ecc5a79379fd5f3bf9a0b76e0793035606fec720efbaaed"
              },
              "article": {
                "id": "b1671730f35a8a0c4675c59d792543a8ec581bac4cebc3beff30662e637e69e6",
                "pubkey": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
                "created_at": 1700000000,
                "kind": 30023,
                "tags": [
                  [
                    "d",
                    "article"
                  ]
                ],
                "content": "Original Rust article",
                "sig": "3d697a644a2be5bffb23a992c02779f4828780de715e36b2bd7564cc4a327ce2594e56119ea8a55626c5921d8fa31e8eef10dcac915fe68f64c33f1d4f282aca"
              },
              "note": {
                "id": "4d3421d313ddf62fee56aee85f274177675afa35810ce601ccceaf9d4c59a174",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 1,
                "tags": [
                  [
                    "content-warning",
                    "warning"
                  ]
                ],
                "content": "迁移\n\"quoted\"",
                "sig": "ca47298d6628e32f8287861c709f5788cfedb791b107b43f744e16ab0b38efb4a0c864dd52d00f3693755cd148b664b573f9d15c71f47e5404109a19a9615fa3"
              },
              "reply": {
                "id": "4bfa4504c636eeba27709159412b6e8277981d8fae2d74ca90c443ab7cd2607a",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 1,
                "tags": [
                  [
                    "e",
                    "32ddc14103c9f2d2957f74f54355e4281381aec64af53bcbde780dae4a58821c",
                    "",
                    "root"
                  ],
                  [
                    "e",
                    "32ddc14103c9f2d2957f74f54355e4281381aec64af53bcbde780dae4a58821c",
                    "",
                    "reply"
                  ],
                  [
                    "p",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ]
                ],
                "content": "迁移\n\"quoted\"",
                "sig": "d2992aa10d8fbdf5d1fd073eda7907561782a379e4799f6a7826024b48f8f4a71d63c1150053504c1f4c939eb5c05716c26915faf2ee91bac2e2c7b0d7ae000d"
              },
              "quote": {
                "id": "1c79b378c4d9a06ab6671cb86e49127ff8acbb32570f56bf578907d4fc1023f5",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 1,
                "tags": [
                  [
                    "q",
                    "32ddc14103c9f2d2957f74f54355e4281381aec64af53bcbde780dae4a58821c",
                    "",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ],
                  [
                    "p",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ]
                ],
                "content": "迁移\n\"quoted\"",
                "sig": "98686ce8ed87e58804d6d74c435ff32e62cebc4298f8fbe6c2819ea687794aab3f7138b83c11ee162b4cbdea390f4d4e604f85ddb4e96cbd068712febf778217"
              },
              "follow": {
                "id": "fa4708a9e1c7b52acdee7c79722a6ecb5d538bab076dcdb158ad38191ea11010",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 3,
                "tags": [
                  [
                    "p",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ]
                ],
                "content": "",
                "sig": "38281065124ba44e5bc9fbe03a007053abd2cddf09949231ea567109c9f42f363e8db6c0ec6cf3971639acf08dcfa2f87687243599c53352a76725e098dc77f5"
              },
              "unfollow": {
                "id": "a37bc5da247be04a3172ce36057d1db994cbacbf94d70d6f16dda759a58d5d39",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 3,
                "tags": [],
                "content": "",
                "sig": "3434f514b219d92a014ce27dcceb9bb31e9306758ff5d3cb0d10c6232fd864cec3ae499a96437679a760d02db079cf0c80e011ff92c3835fae0ad36e0e8cfe52"
              },
              "block": {
                "id": "0f5a4c683e52d4f6da7c8b9f609d307e23c098ab75637bdfe58f58565c37f7ad",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 30000,
                "tags": [
                  [
                    "d",
                    "mute"
                  ],
                  [
                    "p",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ]
                ],
                "content": "",
                "sig": "d152140fcb60c5e901db12cb427ace595199cb062cea549082451def0637f1ce900aa0e8e6274bc6dc2c8fe452d39cf7f11cee18e9287fe30b6dfe5b5440e19a"
              },
              "unblock": {
                "id": "0b1aa3d9c2ce359678b5dc11b82cabf0d4b94f12eb09d5b8e29ff7b96bd3c42b",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 30000,
                "tags": [
                  [
                    "d",
                    "mute"
                  ]
                ],
                "content": "",
                "sig": "8616145914cf5804f4f38c7a346484bb8d90a7c725270ef9576ba244e6d4bc6120fe71adcbe83003060758cb52e3eec84f011fd2531991a0e3b98ba29c5299c3"
              },
              "mute": {
                "id": "1fa0cd5186a48c5c51d57a86e1fa2a8cdc83423707c01a5b7f4a0ba4dbb55720",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 10000,
                "tags": [
                  [
                    "p",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ]
                ],
                "content": "",
                "sig": "fa727f376a67da1923a847791a960dbc6ffe3c15c5cb6e3074076951050f639daba8b4fe199b932d7d4a4db1b42bedad0dae05c39a7aba1f3ebf9fd8f9df5ed5"
              },
              "unmute": {
                "id": "2a05ac121e823af23bc9c433f2d0e7b1b96b225cc196c77d06e1217c41c3b72e",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 10000,
                "tags": [],
                "content": "",
                "sig": "37ab0545694e8bf67fd9bf6d46606f5502dc743ff829d404798837bad4b6a41c6adde63984a9c1f206123083d80245ffc9bd7866d1da8e0984879d53a54385aa"
              },
              "repost": {
                "id": "39c550dbc6b37fc9d244f596d3b9c0cc036dc65a047476cf69645f18867ac493",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 6,
                "tags": [
                  [
                    "e",
                    "32ddc14103c9f2d2957f74f54355e4281381aec64af53bcbde780dae4a58821c"
                  ],
                  [
                    "p",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ]
                ],
                "content": "{\"id\":\"32ddc14103c9f2d2957f74f54355e4281381aec64af53bcbde780dae4a58821c\",\"pubkey\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\",\"created_at\":1700000000,\"kind\":1,\"tags\":[],\"content\":\"Original Rust note\",\"sig\":\"73763b4f233db6265fe5234cdc79b97ad4f749e9d8ab6617a0c8eb3bfce093d8f1514fe5ef0531a61ecc5a79379fd5f3bf9a0b76e0793035606fec720efbaaed\"}",
                "sig": "87994a051df3c0e2667be226d641649eae9c2895ca800e241f00a6f955e101049433b497602fe57ab49d1eb1fcc20e455726240fe8cb865c9072487ef8dac169"
              },
              "genericRepost": {
                "id": "a60181810a8d67cff72afd8693cdb9cb3506d22329e653311e465bacf3eae449",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 16,
                "tags": [
                  [
                    "a",
                    "30023:c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5:article"
                  ],
                  [
                    "e",
                    "b1671730f35a8a0c4675c59d792543a8ec581bac4cebc3beff30662e637e69e6"
                  ],
                  [
                    "p",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ],
                  [
                    "k",
                    "30023"
                  ]
                ],
                "content": "{\"id\":\"b1671730f35a8a0c4675c59d792543a8ec581bac4cebc3beff30662e637e69e6\",\"pubkey\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\",\"created_at\":1700000000,\"kind\":30023,\"tags\":[[\"d\",\"article\"]],\"content\":\"Original Rust article\",\"sig\":\"3d697a644a2be5bffb23a992c02779f4828780de715e36b2bd7564cc4a327ce2594e56119ea8a55626c5921d8fa31e8eef10dcac915fe68f64c33f1d4f282aca\"}",
                "sig": "2a366492396608ac1de98f707fa9d659f4bb45f0213a9ccd5780f54c12338f13b3dff680938eae1f7d92a617aa41277ecda121eac05d26aa97c4890de1c2376b"
              },
              "reaction": {
                "id": "f5ce48465c8e9478a8260e39cc57ecb17ad362305ac8783e683e90331275ac20",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 7,
                "tags": [
                  [
                    "e",
                    "32ddc14103c9f2d2957f74f54355e4281381aec64af53bcbde780dae4a58821c",
                    "",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ],
                  [
                    "p",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ],
                  [
                    "k",
                    "1"
                  ]
                ],
                "content": "+",
                "sig": "a45fb9b55b4e433cd4fbd8639c8bb1e91a1e3e8a55ca3763b5fdb4d718cc3c28a3d95088ead1d73a983f0dda7e26476f86931e7531f279d56b6ea045ee7d72c1"
              },
              "genericReaction": {
                "id": "3ac1770cddf8e4f58b8c0920ebb66b82363a0c3319735330cea27f31f8f7c49e",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 7,
                "tags": [
                  [
                    "e",
                    "b1671730f35a8a0c4675c59d792543a8ec581bac4cebc3beff30662e637e69e6",
                    "",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ],
                  [
                    "a",
                    "30023:c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5:article"
                  ],
                  [
                    "p",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
                  ],
                  [
                    "k",
                    "30023"
                  ]
                ],
                "content": "+",
                "sig": "f984ccf98ff67ae8938c360cd76291b7a6ef1812a19f42c157a88de1b50868ba201272ac04d898ef13d3500fe70530eac6386d81a12aebdaf4f3e5c621fba438"
              },
              "report": {
                "id": "d6e66e39454023e7129902957626cbfc270d3b975086c796daf23dc16d80887e",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 1984,
                "tags": [
                  [
                    "e",
                    "32ddc14103c9f2d2957f74f54355e4281381aec64af53bcbde780dae4a58821c",
                    "spam"
                  ],
                  [
                    "p",
                    "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
                    "spam"
                  ]
                ],
                "content": "",
                "sig": "df0cdcf94371ea7cd3d64f77a87361d4442542cd0e9dcaeefa8b0ccf328f49ae607d9b14e21d655f688c7a56db96f21d8834fe3e4c7c8e668b920882627cc938"
              },
              "delete": {
                "id": "404daa8a282ddcc9822db0c80177dba08235624561f74c3b389bef28de8b1edb",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 5,
                "tags": [
                  [
                    "e",
                    "32ddc14103c9f2d2957f74f54355e4281381aec64af53bcbde780dae4a58821c"
                  ]
                ],
                "content": "",
                "sig": "6099bab06b9c9ca85ec0def8b748e75d194b6a796d2db3e646b514214f0e21471107066d6f3bff8a7cde56eb91c30bc567adc1d49805ad9a25b617896e758970"
              },
              "auth": {
                "id": "6193f06220866b443a3a6fa94df6d636f02637b5421f9753e61dbaf520e1a534",
                "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "created_at": 1700000000,
                "kind": 22242,
                "tags": [
                  [
                    "challenge",
                    "migration-challenge"
                  ],
                  [
                    "relay",
                    "wss://relay1.example"
                  ]
                ],
                "content": "",
                "sig": "8a0e847c56e8525a3da56dbfff71aff7911b8cfc71608423fd752f2eb206e5b34de7b49bf688d93356d448e638fbb838d478d0b9887d7ac412e745b454602dcf"
              }
            }
            """.trimIndent(),
        ).jsonObject
        .mapValues { (_, value) -> QuartzEvent.fromJson(value.toString()) }
