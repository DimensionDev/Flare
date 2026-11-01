import XCTest
import SwiftUI
import UIKit
import KotlinSharedUI
import FlareAppleUI

@MainActor
final class StatusActionSwiftUIAnimationTests: XCTestCase {
    func testInitialSelectedItemsAndGroupsStayStill() async throws {
        for grouped in [false, true] {
            let fixture = try Fixture(actions: actions(liked: true, grouped: grouped))
            defer { fixture.close() }
            await fixture.settle(milliseconds: 50)
            XCTAssertTrue(fixture.hasIconPixels(), "The idle icon must remain visible")
            let first = fixture.pixels()
            await fixture.settle(milliseconds: 350)
            XCTAssertEqual(first, fixture.pixels(), "Initial binding must not animate: group=\(grouped)")
        }
    }

    func testItemAndGroupStateChangesAnimateThenSettle() async throws {
        for grouped in [false, true] {
            let target = actions(liked: true, grouped: grouped)
            let fixture = try Fixture(actions: target)
            defer { fixture.close() }
            await fixture.settle(milliseconds: 100)
            let final = fixture.pixels()
            fixture.update(actions: actions(grouped: grouped))
            await fixture.settle(milliseconds: 350)
            fixture.update(actions: target)
            await fixture.settle(milliseconds: 70)
            attach(fixture, name: "moving-\(grouped)")
            XCTAssertGreaterThan(pixelError(fixture.pixels(), final), 0.015, "A visible state change must animate: group=\(grouped)")
            await fixture.settle(milliseconds: 400)
            attach(fixture, name: "settled-\(grouped)")
            assertPixels(fixture.pixels(), final, "The final icon and count must match a fresh binding")
        }
    }

    func testPostReplacementAndReorderingDoNotReplayAnimations() async throws {
        let target = actions(liked: true)
        let fixture = try Fixture(actions: target, post: "another")
        defer { fixture.close() }
        await fixture.settle(milliseconds: 80)
        let expected = fixture.pixels()
        fixture.update(actions: actions(), post: "post")
        await fixture.settle(milliseconds: 80)
        fixture.update(actions: target, post: "another")
        await fixture.settle(milliseconds: 50)
        XCTAssertEqual(fixture.pixels(), expected, "The same slot and updateKey on a new post must bind statically")

        let reordered: [ActionMenu] = [animatedAction(key: "bookmark", family: .bookmark), animatedAction(liked: true)]
        fixture.update(actions: reordered, post: "baseline")
        await fixture.settle(milliseconds: 80)
        let expectedReordering = fixture.pixels()
        fixture.update(actions: [animatedAction(liked: true), animatedAction(key: "bookmark", family: .bookmark)], post: "another")
        await fixture.settle(milliseconds: 80)
        fixture.update(actions: reordered)
        await fixture.settle(milliseconds: 50)
        XCTAssertEqual(fixture.pixels(), expectedReordering, "Moving actions must reset their animation identity")
    }

    func testReduceMotionUpdatesImmediately() async throws {
        try XCTSkipUnless(UIAccessibility.isReduceMotionEnabled, "Run with ReduceMotionEnabled on the test simulator")
        let fixture = try Fixture(actions: actions(liked: true))
        defer { fixture.close() }
        await fixture.settle(milliseconds: 80)
        let expected = fixture.pixels()
        fixture.update(actions: actions())
        await fixture.settle(milliseconds: 80)
        fixture.update(actions: actions(liked: true))
        await fixture.settle(milliseconds: 50)
        assertPixels(fixture.pixels(), expected)
    }

    func testRapidRollbackAndReappearanceLeaveNoStaleScaleOrCount() async throws {
        let fixture = try Fixture(actions: actions())
        defer { fixture.close() }
        await fixture.settle(milliseconds: 80)
        let expected = fixture.pixels()
        fixture.update(actions: actions(liked: true))
        await fixture.settle(milliseconds: 40)
        fixture.update(actions: actions())
        await fixture.settle(milliseconds: 350)
        attach(fixture, name: "rollback")
        assertPixels(fixture.pixels(), expected)
        fixture.update(actions: actions(liked: true))
        await fixture.settle(milliseconds: 40)
        fixture.setVisible(false)
        await fixture.settle(milliseconds: 40)
        fixture.update(actions: actions())
        fixture.setVisible(true)
        await fixture.settle(milliseconds: 50)
        assertPixels(fixture.pixels(), expected, "Reappearing content must not retain an old animation")
    }

    func testRetainedLazyRowCancelsMotionBeforeReappearing() async throws {
        let initial: [ActionMenu] = [animatedAction(count: 9)]
        let fixture = try Fixture(actions: initial, lazyRows: true)
        defer { fixture.close() }
        await fixture.settle(milliseconds: 100)
        let expected = fixture.pixels()
        let originalIdentity = try XCTUnwrap(fixture.lifecycle.appearances.first)
        fixture.update(actions: [animatedAction(liked: true, count: 9)])
        await fixture.settle(milliseconds: 40)
        try fixture.scroll(to: 2_200)
        await fixture.settle(milliseconds: 40)
        XCTAssertGreaterThan(fixture.lifecycle.disappearances, 0)
        fixture.update(actions: initial)
        try fixture.scroll(to: 0)
        await fixture.settle(milliseconds: 50)
        XCTAssertGreaterThan(fixture.lifecycle.appearances.count, 1)
        XCTAssertEqual(fixture.lifecycle.appearances.last, originalIdentity, "Exercise retained row state, not a fresh row")
        assertPixels(fixture.pixels(), expected, "An interrupted bounce must not resume when the cached row reappears")
    }

    func testPostReplacementDuringMotionBindsSameCountImmediately() async throws {
        let initial: [ActionMenu] = [animatedAction(count: 9)]
        let fixture = try Fixture(actions: initial)
        defer { fixture.close() }
        await fixture.settle(milliseconds: 80)
        let expected = fixture.pixels()
        fixture.update(actions: [animatedAction(liked: true, count: 9)])
        await fixture.settle(milliseconds: 50)
        fixture.update(actions: initial, post: "replacement")
        await fixture.settle(milliseconds: 40)
        assertPixels(fixture.pixels(), expected, "Changing posts must cancel icon motion even when the count stays the same")
    }

    func testCountOnlyUpdateAnimatesAndSettles() async throws {
        let fixture = try Fixture(actions: [animatedAction(count: 10)])
        defer { fixture.close() }
        await fixture.settle(milliseconds: 80)
        let expected = fixture.pixels()
        fixture.update(actions: [animatedAction(count: 9)])
        await fixture.settle(milliseconds: 300)
        fixture.update(actions: [animatedAction(count: 10)])
        await fixture.settle(milliseconds: 60)
        XCTAssertGreaterThan(pixelError(fixture.pixels(), expected), 0.015, "Count transitions must work independently of icon motion")
        await fixture.settle(milliseconds: 300)
        assertPixels(fixture.pixels(), expected)
    }

    func testCountMovesUpWhenIncreasingAndDownWhenDecreasing() async throws {
        let countRect = CGRect(x: 268, y: 0, width: 12, height: 44)
        for (before, after, increasing) in [(Int64(9), Int64(10), true), (10, 9, false)] {
            let fixture = try Fixture(actions: [animatedAction(liked: true, count: after)])
            defer { fixture.close() }
            await fixture.settle(milliseconds: 80)
            let restingY = try XCTUnwrap(actionNumberInkCenterY(fixture.image(), in: countRect))
            fixture.update(actions: [animatedAction(count: before)])
            await fixture.settle(milliseconds: 350)
            fixture.update(actions: [animatedAction(liked: true, count: after)])
            await fixture.settle(milliseconds: 110)
            attach(fixture, name: increasing ? "count-increasing" : "count-decreasing")
            let enteringY = try XCTUnwrap(actionNumberInkCenterY(fixture.image(), in: countRect))
            if increasing {
                XCTAssertGreaterThan(enteringY, restingY + 0.5, "An increasing count must enter from below and move up")
            } else {
                XCTAssertLessThan(enteringY, restingY - 0.5, "A decreasing count must enter from above and move down")
            }
        }
    }

    func testIconOnlyUpdateSpringsWithoutAChangingCount() async throws {
        let target: [ActionMenu] = [animatedAction(liked: true, count: 9)]
        let fixture = try Fixture(actions: target)
        defer { fixture.close() }
        await fixture.settle(milliseconds: 80)
        let expected = fixture.pixels()
        let restingWidth = fixture.iconWidth()
        fixture.update(actions: [animatedAction(count: 9)])
        await fixture.settle(milliseconds: 300)
        fixture.update(actions: target)
        var narrowest = restingWidth
        for delay in [30, 40, 40] {
            await fixture.settle(milliseconds: delay)
            narrowest = min(narrowest, fixture.iconWidth())
        }
        XCTAssertLessThan(narrowest, restingWidth, "The icon must actually scale, independently of the counter")
        await fixture.settle(milliseconds: 300)
        assertPixels(fixture.pixels(), expected)
    }

    func testPostReplacementCancelsCountTransitionWithTheSameTargetText() async throws {
        let target: [ActionMenu] = [animatedAction(count: 10)]
        let fixture = try Fixture(actions: target)
        defer { fixture.close() }
        await fixture.settle(milliseconds: 80)
        let expected = fixture.pixels()
        fixture.update(actions: [animatedAction(count: 9)])
        await fixture.settle(milliseconds: 300)
        fixture.update(actions: target)
        await fixture.settle(milliseconds: 50)
        fixture.update(actions: target, post: "replacement")
        await fixture.settle(milliseconds: 40)
        assertPixels(fixture.pixels(), expected, "Equal target text must not retain the previous post's digit transition")
    }

    private func actions(liked: Bool = false, grouped: Bool = false) -> [ActionMenu] {
        let item = animatedAction(liked: liked, count: liked ? 10 : 9)
        return grouped ? [ActionMenu.Group(displayItem: item, actions: [animatedAction()])] : [item]
    }

    private func attach(_ fixture: Fixture, name: String) {
        let attachment = XCTAttachment(image: fixture.image())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func pixelError(_ actual: Data, _ expected: Data) -> Double {
        guard actual.count == expected.count, !actual.isEmpty else { return .infinity }
        let squaredError = zip(actual, expected).reduce(0.0) { sum, pair in
            let difference = Double(pair.0) - Double(pair.1)
            return sum + difference * difference
        }
        return sqrt(squaredError / Double(actual.count)) / 255
    }

    private func assertPixels(_ actual: Data, _ expected: Data, _ message: String = "",
                              file: StaticString = #filePath, line: UInt = #line) {
        // Native symbol/text transitions can finish on different subpixel rasterizations.
        XCTAssertLessThan(pixelError(actual, expected), 0.015, message, file: file, line: line)
    }

    private struct Content: View {
        var actions: [ActionMenu]
        var post: String
        var isShown = true
        var lazyRows = false
        var lifecycle: RowLifecycle?

        var body: some View {
            Group {
                if lazyRows, let lifecycle {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            RetainedActionRow(actions: actions, post: post, lifecycle: lifecycle)
                                .frame(width: 280, height: 44)
                            ForEach(0..<100, id: \.self) { _ in
                                Color.clear.frame(height: 44)
                            }
                        }
                    }
                    .scrollIndicators(.hidden)
                } else if isShown {
                    StatusActionsView(data: actions, useText: false, postKey: post)
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: 280, height: 44)
            .background(.white)
            .preferredColorScheme(.light)
        }
    }

    @MainActor
    private final class RowLifecycle {
        var appearances: [UUID] = []
        var disappearances = 0
    }

    private struct RetainedActionRow: View {
        let actions: [ActionMenu]
        let post: String
        let lifecycle: RowLifecycle
        @State private var identity = UUID()

        var body: some View {
            StatusActionsView(data: actions, useText: false, postKey: post)
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
                .onAppear { lifecycle.appearances.append(identity) }
                .onDisappear { lifecycle.disappearances += 1 }
        }
    }

    @MainActor
    private final class Fixture {
        let host: UIHostingController<Content>
        let window: UIWindow
        let lifecycle = RowLifecycle()

        init(actions: [ActionMenu], post: String = "post", lazyRows: Bool = false) throws {
            let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
            window = UIWindow(windowScene: scene)
            host = UIHostingController(rootView: Content(actions: actions, post: post, lazyRows: lazyRows, lifecycle: lifecycle))
            host.safeAreaRegions = []
            window.frame = CGRect(x: 0, y: 0, width: 280, height: 44)
            window.rootViewController = host
            window.isHidden = false
            host.view.frame = window.bounds
            host.view.layoutIfNeeded()
        }

        func update(actions: [ActionMenu], post: String? = nil) {
            var content = host.rootView
            content.actions = actions
            if let post { content.post = post }
            host.rootView = content
            host.view.layoutIfNeeded()
        }

        func setVisible(_ visible: Bool) {
            var content = host.rootView
            content.isShown = visible
            host.rootView = content
            host.view.layoutIfNeeded()
        }

        func scroll(to offset: CGFloat) throws {
            func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap(descendants) }
            let scroll = try XCTUnwrap(descendants(host.view).compactMap { $0 as? UIScrollView }.first)
            XCTAssertGreaterThan(scroll.contentSize.height, offset)
            scroll.setContentOffset(CGPoint(x: 0, y: offset), animated: false)
            host.view.layoutIfNeeded()
        }

        func settle(milliseconds: Int) async {
            try? await Task.sleep(for: .milliseconds(milliseconds))
            host.view.layoutIfNeeded()
        }

        func pixels() -> Data {
            image().cgImage!.dataProvider!.data! as Data
        }

        func hasIconPixels() -> Bool {
            let image = image().cgImage!
            let data = image.dataProvider!.data! as Data
            let bytesPerPixel = image.bitsPerPixel / 8
            return (0..<image.height).contains { y in
                (230..<259).contains { x in
                    let offset = y * image.bytesPerRow + x * bytesPerPixel
                    return data[offset..<offset + 3].min()! < 200
                }
            }
        }

        func iconWidth() -> Int {
            let image = image().cgImage!
            let data = image.dataProvider!.data! as Data
            let bytesPerPixel = image.bitsPerPixel / 8
            var columns = Set<Int>()
            // The single-digit counter is outside this crop.
            for y in 0..<image.height {
                for x in 230..<267 {
                    let offset = y * image.bytesPerRow + x * bytesPerPixel
                    if data[offset..<offset + 3].min()! < 200 { columns.insert(x) }
                }
            }
            guard let first = columns.min(), let last = columns.max() else { return 0 }
            return last - first + 1
        }

        func image() -> UIImage {
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            format.preferredRange = .standard
            return UIGraphicsImageRenderer(size: CGSize(width: 280, height: 44), format: format).image { _ in
                host.view.drawHierarchy(in: CGRect(x: 0, y: 0, width: 280, height: 44), afterScreenUpdates: true)
            }
        }

        func close() {
            window.isHidden = true
            window.rootViewController = nil
        }
    }
}
