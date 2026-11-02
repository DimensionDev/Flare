import XCTest
import SwiftUI
import KotlinSharedUI
import FlareAppleUI
@testable import Flare

@MainActor
final class StatusActionLayoutTests: XCTestCase {
    func testMenuReuseKeepsItsInteractionAndUpdatesDisabledState() throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let root = UIViewController()
        window.rootViewController = root
        window.makeKeyAndVisible()
        defer { window.isHidden = true; previous?.makeKeyAndVisible() }
        let actions = StatusActionsUIView(frame: CGRect(x: 0, y: 100, width: 362, height: 40))
        root.view.addSubview(actions)
        func configure(enabled: Bool) {
            let item = ActionMenu.Item(updateKey: "menu", icon: .more, text: nil, count: nil,
                                       color: nil, clickEvent: ClickEventNoop.shared, actionFamily: nil, enabled: enabled)
            actions.configure(data: [ActionMenu.Group(displayItem: item, actions: [item])], useText: false,
                              postActionStyle: .leftAligned, applyPostActionLayout: false,
                              showNumbers: true, isDetail: false)
            actions.layoutIfNeeded()
        }
        configure(enabled: true)
        let button = try XCTUnwrap(actions.subviews.compactMap { $0 as? UIButton }.first)
        let interaction = try XCTUnwrap(button.interactions.compactMap { $0 as? UIContextMenuInteraction }.first)
        let menu = try XCTUnwrap(button.menu)
        configure(enabled: true)
        XCTAssertTrue(interaction === button.interactions.compactMap { $0 as? UIContextMenuInteraction }.first)
        XCTAssertFalse(button.menu === menu, "Reusing the interaction must still replace its menu payload")
        configure(enabled: false)
        XCTAssertFalse(button.isEnabled)
        XCTAssertEqual(button.alpha, 0.4, accuracy: 0.001)
        configure(enabled: true)
        XCTAssertTrue(button.isEnabled)
        XCTAssertTrue(button.showsMenuAsPrimaryAction)
        XCTAssertEqual(button.alpha, 1)
    }

    func testRightToLeftCountAnimationKeepsTheOutgoingTrailingEdge() async throws {
        if UIAccessibility.isReduceMotionEnabled { throw XCTSkip("Requires count animations") }
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let root = UIViewController()
        window.rootViewController = root
        window.makeKeyAndVisible()
        defer { window.isHidden = true; previous?.makeKeyAndVisible() }
        let actions = StatusActionsUIView(frame: CGRect(x: 0, y: 100, width: 362, height: 40))
        actions.semanticContentAttribute = .forceRightToLeft
        root.view.addSubview(actions)
        func configure(count: Int64) {
            let item = ActionMenu.Item(updateKey: "like", icon: .unlike, text: nil, count: UiNumber(value: count),
                                       color: .red, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
            actions.configure(data: [item], useText: false, postActionStyle: .leftAligned,
                              postActionFixedWidth: false, applyPostActionLayout: false,
                              showNumbers: true, isDetail: false, postKey: "rtl-count")
            actions.layoutIfNeeded()
        }
        configure(count: 10)
        configure(count: 9)
        let button = try XCTUnwrap(actions.subviews.compactMap { $0 as? UIButton }.first)
        button.layoutIfNeeded()
        let labels = button.subviews.compactMap { $0 as? UILabel }
        let incoming = try XCTUnwrap(labels.first(where: { $0.alpha == 1 }))
        let outgoing = try XCTUnwrap(labels.first(where: { $0.alpha == 0 }))
        XCTAssertNotNil(incoming.layer.animation(forKey: "statusAction.numberSlide"))
        XCTAssertGreaterThan(outgoing.frame.width, incoming.frame.width)
        XCTAssertEqual(outgoing.frame.maxX, incoming.frame.maxX, accuracy: 0.001)
        actions.prepareForPoolRemoval()
        XCTAssertNil(incoming.layer.animationKeys())
        XCTAssertNil(outgoing.text)
    }

    func testAnimatedIconsKeepTheirScaledGeometryAtAccessibilitySizes() async throws {
        if UIAccessibility.isReduceMotionEnabled { throw XCTSkip("Requires icon animations") }
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let root = UIViewController()
        window.rootViewController = root
        window.traitOverrides.preferredContentSizeCategory = .accessibilityExtraExtraExtraLarge
        window.makeKeyAndVisible()
        defer { window.isHidden = true; previous?.makeKeyAndVisible() }
        let actions = StatusActionsUIView(frame: CGRect(x: 0, y: 100, width: 362, height: 100))
        root.view.addSubview(actions)
        try await Task.sleep(for: .milliseconds(80))
        func configure(color: ActionMenu.ItemColor?) {
            let item = ActionMenu.Item(updateKey: "like", icon: .unlike, text: nil, count: UiNumber(value: 128),
                                       color: color, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
            actions.configure(data: [item], useText: false, postActionStyle: .leftAligned,
                              applyPostActionLayout: false, showNumbers: true, isDetail: false, postKey: "large-icons")
            actions.layoutIfNeeded()
        }
        configure(color: nil)
        configure(color: .red)
        let button = try XCTUnwrap(actions.subviews.compactMap { $0 as? UIButton }.first)
        let images = button.subviews.compactMap { $0 as? UIImageView }
        let incoming = try XCTUnwrap(images.first(where: { $0.alpha == 1 }))
        let outgoing = try XCTUnwrap(images.first(where: { $0.alpha == 0 }))
        let image = try XCTUnwrap(incoming.image)
        let expectedScale = min(1, 20 / image.size.width)
        XCTAssertLessThan(expectedScale, 1)
        XCTAssertEqual(incoming.transform.a, expectedScale, accuracy: 0.001)
        XCTAssertEqual(outgoing.bounds, incoming.bounds)
        XCTAssertEqual(outgoing.center, incoming.center)
        XCTAssertEqual(outgoing.transform, incoming.transform)
        let spring = try XCTUnwrap(incoming.layer.animation(forKey: "statusAction.pop") as? CASpringAnimation)
        XCTAssertEqual(try XCTUnwrap(spring.toValue as? Double), Double(expectedScale), accuracy: 0.001)
    }

    func testConfiguredSymbolsAreSharedAcrossReusedCells() throws {
        let metrics = StatusActionLayoutMetrics(fontSize: 13, scale: 3)
        let first = try XCTUnwrap(metrics.iconImage(.retweet))
        XCTAssertTrue(first === metrics.iconImage(.retweet))
        let large = try XCTUnwrap(StatusActionLayoutMetrics(fontSize: 28, scale: 3).iconImage(.retweet))
        XCTAssertFalse(first === large)
        XCTAssertEqual(first.renderingMode, .alwaysTemplate)
    }

    func testUIKitCachedFramesFollowResizeDirectionAndReconfiguration() throws {
        let actions = StatusActionsUIView(frame: CGRect(x: 0, y: 0, width: 362, height: 40))
        let item = ActionMenu.Item(updateKey: "layout", icon: .unlike, text: nil, count: UiNumber(value: 128),
                                   color: .red, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
        func configure(numbers: Bool) {
            actions.configure(data: [item], useText: false, postActionStyle: .rightAligned,
                              applyPostActionLayout: false, showNumbers: numbers, isDetail: false)
            actions.layoutIfNeeded()
        }
        configure(numbers: true)
        let button = try XCTUnwrap(actions.subviews.compactMap { $0 as? UIButton }.first)
        let originalWidth = button.frame.width
        XCTAssertEqual(button.frame.maxX, 362, accuracy: 0.001)
        actions.frame.size.width = 295
        actions.layoutIfNeeded()
        XCTAssertEqual(button.frame.maxX, 295, accuracy: 0.001)
        actions.semanticContentAttribute = .forceRightToLeft
        actions.setNeedsLayout()
        actions.layoutIfNeeded()
        XCTAssertEqual(button.frame.minX, 0, accuracy: 0.001)
        configure(numbers: false)
        XCTAssertLessThan(button.frame.width, originalWidth)
        XCTAssertEqual(button.frame.minX, 0, accuracy: 0.001)
        actions.prepareForPoolRemoval()
        XCTAssertEqual(actions.timelineHeight(for: 295), 0)
        XCTAssertFalse(actions.hasVisibleActions)
        configure(numbers: true)
        XCTAssertEqual(actions.subviews.first?.frame.width ?? 0, originalWidth, accuracy: 0.001)

        actions.frame.size.width = 156
        actions.configure(data: Array(repeating: item, count: 5), useText: false, postActionStyle: .rightAligned,
                          applyPostActionLayout: false, showNumbers: true, isDetail: false)
        actions.layoutIfNeeded()
        let narrowButton = try XCTUnwrap(actions.subviews.compactMap { $0 as? UIButton }.first)
        narrowButton.layoutIfNeeded()
        let label = try XCTUnwrap(narrowButton.subviews.compactMap { $0 as? UILabel }.first)
        XCTAssertTrue(label.isHidden)
        XCTAssertEqual(narrowButton.accessibilityValue, item.count?.humanized)
        actions.frame.size.width = 400
        actions.layoutIfNeeded()
        narrowButton.layoutIfNeeded()
        XCTAssertFalse(label.isHidden)
    }

    func testUIKitActionsUpdateCachedMeasurementsForDynamicType() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let root = UIViewController()
        window.rootViewController = root
        window.makeKeyAndVisible()
        defer { window.isHidden = true; previous?.makeKeyAndVisible() }
        let actions = StatusActionsUIView(frame: CGRect(x: 0, y: 100, width: 362, height: 80))
        root.view.addSubview(actions)
        let item = ActionMenu.Item(updateKey: "dynamic", icon: .unlike, text: nil, count: UiNumber(value: 128),
                                   color: .red, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
        actions.configure(data: [item], useText: false, postActionStyle: .leftAligned,
                          applyPostActionLayout: false, showNumbers: true, isDetail: false)
        let originalHeight = try XCTUnwrap(actions.timelineHeight(for: 362))
        window.traitOverrides.preferredContentSizeCategory = .accessibilityExtraExtraExtraLarge
        try await Task.sleep(for: .milliseconds(80))
        actions.layoutIfNeeded()
        XCTAssertGreaterThan(try XCTUnwrap(actions.timelineHeight(for: 362)), originalHeight)
        let button = try XCTUnwrap(actions.subviews.compactMap { $0 as? UIButton }.first)
        let image = try XCTUnwrap(button.subviews.compactMap { $0 as? UIImageView }.first)
        XCTAssertLessThanOrEqual(image.frame.width, 20.001)
    }

    func testStretchKeepsLeadingSlotsIndependentOfCountsAndSingleActionNatural() {
        let metrics = StatusActionLayoutMetrics(fontSize: 13, scale: 3)
        let sizes = [CGSize(width: 40, height: 24), CGSize(width: 100, height: 28), CGSize(width: 28, height: 23)]
        let frames = metrics.frames(width: 300, sizes: sizes, style: .stretch, allowSpacer: true, lastHasNumber: false)
        XCTAssertEqual(frames.map(\.minX), [0, 136, 272])
        XCTAssertTrue(frames.allSatisfy { $0.maxY == 28 })
        let changedCounts = metrics.frames(width: 300, sizes: sizes.map { CGSize(width: $0.width * 2, height: $0.height) },
                                          style: .stretch, allowSpacer: true, lastHasNumber: false)
        XCTAssertEqual(frames.map(\.minX), changedCounts.map(\.minX))
        let single = metrics.frames(width: 300, sizes: [sizes[0]], style: .stretch, allowSpacer: true, lastHasNumber: false)
        XCTAssertEqual(single[0].width, 40)
        let trailingCount = metrics.frames(width: 300, sizes: sizes, style: .stretch, allowSpacer: true, lastHasNumber: true)
        XCTAssertEqual(trailingCount.last!.width, 28 + 13 * 2.5 + 2, accuracy: 1.0 / 3)
        XCTAssertEqual(trailingCount.last!.maxX, 300)
    }

    func testFixedCountPlaceholderUsesTheActualFontAndSurvivesHiddenOrZeroCounts() {
        for fontSize: CGFloat in [13, 17, 28] {
            let metrics = StatusActionLayoutMetrics(fontSize: fontSize, scale: 3)
            let zero = metrics.size(titleWidth: nil, reservesNumberWidth: true)
            let short = metrics.size(titleWidth: metrics.textWidth("8"), reservesNumberWidth: true)
            XCTAssertEqual(zero, short)
            XCTAssertEqual(zero.width, 20 + 2 + metrics.numberWidth + 8)
            XCTAssertGreaterThan(metrics.numberWidth, metrics.textWidth("000"))
            let narrow = metrics.frames(width: 240, sizes: Array(repeating: short, count: 4) + [CGSize(width: 28, height: 23)],
                                        style: .leftAligned, allowSpacer: true, lastHasNumber: false)
            XCTAssertEqual(narrow.last!.maxX, 240, accuracy: 1.0 / 3)
            XCTAssertTrue(narrow.allSatisfy { $0.width >= 28 })
            for index in 1..<narrow.count { XCTAssertGreaterThanOrEqual(narrow[index].minX, narrow[index - 1].maxX) }
        }
    }

    func testLargeIconsFitTheFixedColumnWithoutChangingItsWidth() throws {
        for size: CGFloat in [13, 17, 28, 44] {
            let metrics = StatusActionLayoutMetrics(fontSize: size, scale: 3)
            for icon in [UiIcon.reply, .retweet, .unlike, .bookmark] {
                let image = try XCTUnwrap(metrics.iconImage(icon))
                let scale = metrics.iconScale(imageWidth: image.size.width)
                XCTAssertGreaterThan(scale, 0)
                XCTAssertLessThanOrEqual(scale, 1)
                XCTAssertLessThanOrEqual(image.size.width * scale, 20.001)
            }
        }
    }

    func testRightAlignmentAndMirroringUseTheSamePixelPositions() {
        let metrics = StatusActionLayoutMetrics(fontSize: 13, scale: 3)
        let sizes = [CGSize(width: 47.333, height: 24), CGSize(width: 28, height: 23)]
        let left = metrics.frames(width: 295, sizes: sizes, style: .rightAligned, allowSpacer: true, lastHasNumber: false)
        let right = metrics.frames(width: 295, sizes: sizes, style: .rightAligned, allowSpacer: true, lastHasNumber: false, rightToLeft: true)
        XCTAssertEqual(left.last!.maxX, 295)
        for index in left.indices {
            XCTAssertEqual(left[index].minX, 295 - right[index].maxX, accuracy: 0.001)
            XCTAssertEqual(left[index].width, right[index].width)
        }
    }
}
