package dev.dimension.flare.ui.screen.counter

import dev.dimension.flare.common.CruxBinding
import dev.dimension.flare.common.CruxBridge
import dev.dimension.flare.counter.CounterEffect
import dev.dimension.flare.counter.CounterEvent
import dev.dimension.flare.counter.CounterState
import dev.dimension.flare.counter.Requests
import dev.dimension.flare.rust.createCounterCore

internal fun counterBinding(initialValue: Long) =
    CruxBinding<CounterState, CounterEvent>(
        createBridge = {
            val core = createCounterCore(initialValue)
            object : CruxBridge {
                override fun update(event: ByteArray) = core.update(event)

                override fun view() = core.view()

                override fun close() = core.close()
            }
        },
        encodeEvent = { it.bincodeSerialize() },
        decodeState = { CounterState.bincodeDeserialize(it) },
        processEffects = { bytes, render ->
            for (request in Requests.bincodeDeserialize(bytes).value) {
                when (request.effect) {
                    is CounterEffect.Render -> render()
                }
            }
        },
    )
