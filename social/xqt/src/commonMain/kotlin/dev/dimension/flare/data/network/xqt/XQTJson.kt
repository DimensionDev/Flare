package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.common.JSON_WITH_ENCODE_DEFAULT
import dev.dimension.flare.data.network.xqt.model.InstructionUnion
import dev.dimension.flare.data.network.xqt.model.UnknownInstruction
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

internal val XQT_JSON =
    Json(JSON_WITH_ENCODE_DEFAULT) {
        serializersModule =
            SerializersModule {
                include(JSON_WITH_ENCODE_DEFAULT.serializersModule)
                polymorphic(InstructionUnion::class) {
                    defaultDeserializer { type ->
                        if (type == null) {
                            null
                        } else {
                            UnknownInstruction.serializer()
                        }
                    }
                }
            }
    }
