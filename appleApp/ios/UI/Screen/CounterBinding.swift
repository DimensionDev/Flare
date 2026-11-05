import FlareCounterTypes
import FlareRust
import Foundation

extension CounterCore: CruxBridge {}

@MainActor
func counterBinding(initialValue: Int64) -> CruxBinding<CounterState, CounterEvent> {
    CruxBinding(
        createBridge: { createCounterCore(initialValue: initialValue) },
        encodeEvent: { Data(try $0.bincodeSerialize()) },
        decodeState: { try CounterState.bincodeDeserialize(input: [UInt8]($0)) },
        processEffects: { bytes, render in
            for request in try Requests.bincodeDeserialize(input: [UInt8](bytes)).value {
                switch request.effect {
                case .render:
                    render()
                }
            }
        }
    )
}
