package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.Serializable

// Unrecognized server instructions must not prevent known timeline entries from loading.
@Serializable
internal data object UnknownInstruction : InstructionUnion
