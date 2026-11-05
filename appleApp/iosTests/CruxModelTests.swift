import Foundation
import XCTest

@MainActor
final class CruxModelTests: XCTestCase {
    func testActionFailureKeepsContentAndRecovers() {
        let bridge = FakeBridge(7)
        let model = CruxModel(binding: binding { bridge })
        bridge.failUpdate = true
        model.send(1)
        XCTAssertEqual(model.state, 7)
        XCTAssertNotNil(model.error)
        bridge.failUpdate = false
        model.send(2)
        XCTAssertEqual(model.state, 9)
        XCTAssertNil(model.error)
    }

    func testInvalidEffectsAndViewFailuresKeepTheLastDecodedState() {
        let bridge = FakeBridge(7)
        var invalidEffects = true
        let model = CruxModel(binding: binding({ bridge }) { _, render in
            if invalidEffects { throw Failure.invalidEffects }
            render()
        })
        model.send(1)
        XCTAssertEqual(model.state, 7)
        XCTAssertNotNil(model.error)
        invalidEffects = false
        bridge.failView = true
        model.send(1)
        XCTAssertEqual(model.state, 7)
        XCTAssertNotNil(model.error)
        bridge.failView = false
        model.send(1)
        XCTAssertEqual(model.state, 10)
        XCTAssertNil(model.error)
    }

    func testInitializationFailureReleasesTheBridge() {
        weak var native: FakeBridge?
        let model = CruxModel(binding: binding {
            let bridge = FakeBridge(7)
            bridge.failView = true
            native = bridge
            return bridge
        })
        XCTAssertNil(model.state)
        XCTAssertNotNil(model.error)
        XCTAssertNil(native)
        model.send(1)
        model.close()
    }

    func testFactoryFailureIsReportedWithoutAState() {
        let model = CruxModel(binding: binding { throw Failure.factory })
        XCTAssertNil(model.state)
        XCTAssertNotNil(model.error)
        model.send(1)
    }

    func testClosingIgnoresPendingRendersAndReleasesTheBridge() {
        weak var native: FakeBridge?
        var render: (@MainActor () -> Void)?
        let model = CruxModel(binding: binding({
            let bridge = FakeBridge(7)
            native = bridge
            return bridge
        }) { _, callback in render = callback })
        model.send(1)
        XCTAssertNotNil(native)
        model.close()
        model.close()
        XCTAssertNil(native)
        render?()
        model.send(1)
        XCTAssertEqual(model.state, 7)
    }

    func testPendingRenderDoesNotRetainTheModelOrBridge() {
        weak var native: FakeBridge?
        weak var owner: CruxModel<Int, Int>?
        var render: (@MainActor () -> Void)?
        var model: CruxModel<Int, Int>? = CruxModel(binding: binding({
            let bridge = FakeBridge(7)
            native = bridge
            return bridge
        }) { _, callback in render = callback })
        owner = model
        model?.send(1)
        model = nil
        XCTAssertNil(owner)
        XCTAssertNil(native)
        render?()
    }

    func testDifferentStateAndEventTypesHaveIndependentOwners() {
        let firstBridge = FakeBridge(7)
        let secondBridge = FakeBridge(9)
        let first = CruxModel(binding: binding { firstBridge })
        let second = CruxModel(binding: CruxBinding<String, String>(
            createBridge: { secondBridge },
            encodeEvent: { Data($0.utf8) },
            decodeState: { "value:\(String(decoding: $0, as: UTF8.self))" },
            processEffects: { _, render in render() }
        ))
        first.send(1)
        second.send("2")
        XCTAssertEqual(first.state, 8)
        XCTAssertEqual(second.state, "value:11")
        first.close()
        second.send("1")
        XCTAssertEqual(second.state, "value:12")
    }

    private func binding(
        _ createBridge: @escaping () throws -> any CruxBridge,
        processEffects: @escaping (Data, @escaping @MainActor () -> Void) throws -> Void = { _, render in render() }
    ) -> CruxBinding<Int, Int> {
        CruxBinding(
            createBridge: createBridge,
            encodeEvent: { Data(String($0).utf8) },
            decodeState: { bytes in
                guard let value = Int(String(decoding: bytes, as: UTF8.self)) else {
                    throw Failure.invalidView
                }
                return value
            },
            processEffects: processEffects
        )
    }

    private enum Failure: Error {
        case factory, update, invalidView, invalidEffects
    }

    private final class FakeBridge: CruxBridge {
        var value: Int
        var failUpdate = false
        var failView = false

        init(_ value: Int) { self.value = value }

        func update(event: Data) throws -> Data {
            if failUpdate { throw Failure.update }
            guard let amount = Int(String(decoding: event, as: UTF8.self)) else {
                throw Failure.update
            }
            value += amount
            return Data([0])
        }

        func view() throws -> Data {
            if failView { throw Failure.invalidView }
            return Data(String(value).utf8)
        }
    }
}
