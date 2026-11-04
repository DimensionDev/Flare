import FlareAppleCore
import KotlinSharedUI
import Observation
import SwiftUI
import UIKit
import XCTest

@MainActor
final class StateViewTests: XCTestCase {
    func testInitialSuccessIsDeliveredOnceAcrossUnrelatedUpdates() async throws {
        let fixture = try Fixture(state: UiStateSuccess(data: "enabled" as NSString))
        defer { fixture.close() }

        try await fixture.waitForValues(["enabled"])
        fixture.source.revision += 1
        fixture.source.state = UiStateSuccess(data: "enabled" as NSString)
        try await fixture.settle()
        XCTAssertEqual(fixture.values, ["enabled"])

        fixture.source.state = UiStateSuccess(data: "disabled" as NSString)
        try await fixture.waitForValues(["enabled", "disabled"])
    }

    func testLoadingStateDeliversLaterSuccessAndToggleChanges() async throws {
        let fixture = try Fixture(state: UiStateLoading())
        defer { fixture.close() }

        try await fixture.settle()
        XCTAssertTrue(fixture.values.isEmpty)

        fixture.source.state = UiStateSuccess(data: "enabled" as NSString)
        try await fixture.waitForValues(["enabled"])
        fixture.source.state = UiStateSuccess(data: "disabled" as NSString)
        try await fixture.waitForValues(["enabled", "disabled"])
        fixture.source.state = UiStateSuccess(data: "enabled" as NSString)
        try await fixture.waitForValues(["enabled", "disabled", "enabled"])
    }

    func testErrorsAndLoadingDoNotDeliverSuccess() async throws {
        let error = UiStateError<NSString>(throwable: KotlinThrowable(message: "Settings unavailable"))
        let fixture = try Fixture(state: error)
        defer { fixture.close() }

        try await fixture.settle()
        XCTAssertTrue(fixture.values.isEmpty)
        fixture.source.state = UiStateLoading()
        try await fixture.settle()
        XCTAssertTrue(fixture.values.isEmpty)

        fixture.source.state = UiStateSuccess(data: "recovered" as NSString)
        try await fixture.waitForValues(["recovered"])
        fixture.source.state = error
        try await fixture.settle()
        XCTAssertEqual(fixture.values, ["recovered"])
    }

    @MainActor
    @Observable
    fileprivate final class Source {
        var state: UiState<NSString>
        var revision = 0

        init(state: UiState<NSString>) {
            self.state = state
        }
    }

    private struct Content: View {
        let source: Source
        let onSuccess: (NSString) -> Void

        var body: some View {
            Color.clear
                .frame(width: CGFloat(100 + source.revision), height: 20)
                .onSuccessOf(of: source.state, data: onSuccess)
        }
    }

    @MainActor
    private final class Fixture {
        let source: Source
        let window: UIWindow
        let previousWindow: UIWindow?
        var values: [String] = []

        init(state: UiState<NSString>) throws {
            let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
            source = Source(state: state)
            previousWindow = scene.windows.first { $0.isKeyWindow }
            window = UIWindow(windowScene: scene)
            window.rootViewController = UIHostingController(rootView: Content(source: source) { [weak self] value in
                self?.values.append(value as String)
            })
            window.makeKeyAndVisible()
        }

        func waitForValues(_ expected: [String], file: StaticString = #filePath, line: UInt = #line) async throws {
            for _ in 0..<200 {
                if values == expected { return }
                try await Task.sleep(for: .milliseconds(10))
            }
            XCTAssertEqual(values, expected, file: file, line: line)
        }

        func settle() async throws {
            try await Task.sleep(for: .milliseconds(100))
        }

        func close() {
            window.isHidden = true
            window.rootViewController = nil
            previousWindow?.makeKeyAndVisible()
        }
    }
}
