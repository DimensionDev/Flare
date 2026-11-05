import Combine
import Foundation

@MainActor
protocol CruxBridge: AnyObject {
    func update(event: Data) throws -> Data
    func view() throws -> Data
}

@MainActor
struct CruxBinding<State, Event> {
    let createBridge: () throws -> any CruxBridge
    let encodeEvent: (Event) throws -> Data
    let decodeState: (Data) throws -> State
    let processEffects: (Data, @escaping @MainActor () -> Void) throws -> Void
}

@MainActor
final class CruxModel<State, Event>: ObservableObject {
    @Published private(set) var state: State?
    @Published private(set) var error: String?
    private let binding: CruxBinding<State, Event>
    private var bridge: (any CruxBridge)?

    init(binding: CruxBinding<State, Event>) {
        self.binding = binding
        do {
            let native = try binding.createBridge()
            bridge = native
            state = try binding.decodeState(native.view())
        } catch {
            bridge = nil
            self.error = String(describing: error)
        }
    }

    func send(_ event: Event) {
        guard let native = bridge else { return }
        do {
            error = nil
            let effects = try native.update(event: binding.encodeEvent(event))
            try binding.processEffects(effects) { [weak self, weak native] in
                guard let self, let native, self.bridge === native else { return }
                do {
                    self.state = try self.binding.decodeState(native.view())
                } catch {
                    self.error = String(describing: error)
                }
            }
        } catch {
            self.error = String(describing: error)
        }
    }

    func close() {
        bridge = nil
    }
}
