package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("TimelineTweetComposer")
internal class TimelineTweetComposer(
//    @Contextual @SerialName(value = "__typename")
//    val typename: TypeName? = null,
) :
    ItemContentUnion
