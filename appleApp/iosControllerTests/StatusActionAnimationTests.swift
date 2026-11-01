import XCTest
import UIKit
import SwiftUI
import KotlinSharedUI
import FlareAppleUI
@testable import Flare

@MainActor
final class StatusActionAnimationTests: XCTestCase {
    func testOnlyChangesOfTheSameVisibleActionAnimate() {
        let before = state(liked: false, count: 1)
        let after = state(liked: true, count: 2)
        let selected = after.effects(from: before, isVisible: true, reduceMotion: false)
        XCTAssertEqual(selected.icon, .pop)
        XCTAssertEqual(selected.number, .up)
        let rollback = before.effects(from: after, isVisible: true, reduceMotion: false)
        XCTAssertEqual(rollback.icon, .fade)
        XCTAssertEqual(rollback.number, .down)

        for (previous, visible, reduced) in [(nil, true, false), (before, false, false),
                                            (before, true, true), (after, true, false)] as [(StatusActionAnimationState?, Bool, Bool)] {
            let effects = after.effects(from: previous, isVisible: visible, reduceMotion: reduced)
            XCTAssertEqual(effects.icon, .none)
            XCTAssertEqual(effects.number, .none)
        }
    }

    func testPostActionAndPositionChangesResetAnimationIdentity() {
        let before = state(liked: false, count: 1)
        for after in [state(post: "other", liked: true, count: 2),
                      state(position: 1, liked: true, count: 2),
                      state(liked: true, count: 2, key: "bookmark"),
                      state(post: nil, liked: true, count: 2)] {
            let effects = after.effects(from: before, isVisible: true, reduceMotion: false)
            XCTAssertEqual(effects.icon, .none)
            XCTAssertEqual(effects.number, .none)
        }
        let unkeyed = animatedAction(key: "", family: nil)
        XCTAssertNil(StatusActionAnimationState.Identity(postKey: "post", item: unkeyed, position: 0))
        let familyOnly = animatedAction(key: "")
        XCTAssertEqual(StatusActionAnimationState.Identity(postKey: "post", item: familyOnly, position: 0),
                       StatusActionAnimationState.Identity(postKey: "post", item: animatedAction(liked: true, key: ""), position: 0))
        XCTAssertNotEqual(StatusActionAnimationState.Identity(postKey: "post", item: animatedAction(), position: 0),
                          StatusActionAnimationState.Identity(postKey: "post", item: animatedAction(family: .bookmark), position: 0))
    }

    func testCountsUseRawDirectionButOnlyAnimateVisibleTextChanges() {
        XCTAssertEqual(state(count: 1_000).effects(from: state(count: 999), isVisible: true, reduceMotion: false).number, .up)
        let compact = state(count: 1_000_001)
        XCTAssertEqual(compact.text, state(count: 1_000_000).text)
        XCTAssertEqual(compact.effects(from: state(count: 1_000_000), isVisible: true, reduceMotion: false).number, .none)
        XCTAssertEqual(state(count: 2, showNumbers: false).effects(from: state(count: 1), isVisible: true, reduceMotion: false).number, .none)
        XCTAssertEqual(state(count: 0).effects(from: state(count: 1), isVisible: true, reduceMotion: false).number, .none)
        XCTAssertEqual(state(count: 1).effects(from: state(count: 0), isVisible: true, reduceMotion: false).number, .none)
        let disabled = state(liked: true, count: 2, enabled: false)
        XCTAssertEqual(disabled.effects(from: state(count: 1), isVisible: true, reduceMotion: false).icon, .none)
    }

    func testUIKitUpdatesAnimateWithoutRestartingOnIdenticalReconfiguration() throws {
        let view = StatusActionsUIView()
        configure(view, actions: [animatedAction()])
        let window = try show(view)
        defer { window.isHidden = true; window.rootViewController = nil }
        let (icon, label) = try content(view)
        XCTAssertNil(icon.layer.animationKeys())
        XCTAssertNil(label.layer.animationKeys())
        configure(view, actions: [animatedAction(liked: true, count: 2)])
        let pop = try XCTUnwrap(icon.layer.animation(forKey: "statusAction.pop")?.copy() as? CASpringAnimation)
        XCTAssertEqual(pop.fromValue as? Double, StatusActionAnimationTiming.iconStartScale)
        XCTAssertEqual(pop.toValue as? Double, 1)
        XCTAssertEqual(pop.damping, StatusActionAnimationTiming.iconSpring.damping)
        XCTAssertEqual(pop.duration, StatusActionAnimationTiming.iconSpring.settlingDuration)
        XCTAssertEqual(label.text, "2")
        XCTAssertEqual((view.subviews.first as? UIButton)?.accessibilityValue, "2")
        let transition = try XCTUnwrap(label.layer.animation(forKey: "transition") as? CATransition)
        XCTAssertEqual(transition.subtype, .fromTop)
        pop.setValue("original", forKey: "testMarker")
        icon.layer.add(pop, forKey: "statusAction.pop")
        configure(view, actions: [animatedAction(liked: true, count: 2)])
        XCTAssertEqual(icon.layer.animation(forKey: "statusAction.pop")?.value(forKey: "testMarker") as? String, "original")
        configure(view, actions: [animatedAction()])
        XCTAssertNil(icon.layer.animation(forKey: "statusAction.pop"))
        XCTAssertNotNil(icon.layer.animation(forKey: "transition"))
        XCTAssertEqual((label.layer.animation(forKey: "transition") as? CATransition)?.subtype, .fromBottom)
    }

    func testUIKitReuseReorderingAndRemovalClearRunningAnimations() throws {
        let view = StatusActionsUIView()
        configure(view, actions: [animatedAction(), animatedAction(key: "other", family: .bookmark)])
        let window = try show(view)
        defer { window.isHidden = true; window.rootViewController = nil }
        configure(view, actions: [animatedAction(liked: true, count: 2), animatedAction(key: "other", family: .bookmark)])
        let (icon, label) = try content(view)
        XCTAssertNotNil(icon.layer.animation(forKey: "statusAction.pop"))
        configure(view, actions: [animatedAction(key: "other", family: .bookmark), animatedAction(liked: true, count: 2)])
        XCTAssertNil(icon.layer.animationKeys())
        XCTAssertNil(label.layer.animationKeys())
        configure(view, actions: [animatedAction(count: 50)], post: "another")
        XCTAssertNil(icon.layer.animationKeys())
        XCTAssertNil(label.layer.animationKeys())
        configure(view, actions: [animatedAction(liked: true, count: 51)], post: "another")
        XCTAssertNotNil(icon.layer.animation(forKey: "statusAction.pop"))
        view.removeFromSuperview()
        XCTAssertNil(icon.layer.animationKeys())
        XCTAssertNil(label.layer.animationKeys())
        configure(view, actions: [animatedAction(count: 52)], post: "another")
        XCTAssertNil(icon.layer.animationKeys())
        window.rootViewController?.view.addSubview(view)
        configure(view, actions: [animatedAction(liked: true, count: 53)], post: "another")
        XCTAssertNotNil(icon.layer.animation(forKey: "statusAction.pop"))
        view.prepareForPoolRemoval()
        XCTAssertNil(icon.layer.animationKeys())
        XCTAssertNil(label.layer.animationKeys())
    }

    func testNumberDecelerationMatchesBothRenderers() throws {
        let view = StatusActionsUIView()
        configure(view, actions: [animatedAction()])
        let window = try show(view)
        defer { window.isHidden = true; window.rootViewController = nil }
        configure(view, actions: [animatedAction(count: 2)])
        let (_, label) = try content(view)
        let animation = try XCTUnwrap(label.layer.animation(forKey: "transition"))
        let timing = try XCTUnwrap(animation.timingFunction)
        var start = [Float](repeating: 0, count: 2)
        var end = [Float](repeating: 0, count: 2)
        timing.getControlPoint(at: 1, values: &start)
        timing.getControlPoint(at: 2, values: &end)
        let appliedCurve = UnitCurve.bezier(
            startControlPoint: UnitPoint(x: Double(start[0]), y: Double(start[1])),
            endControlPoint: UnitPoint(x: Double(end[0]), y: Double(end[1]))
        )
        XCTAssertGreaterThan(appliedCurve.value(at: 0.25), 0.7)
        XCTAssertGreaterThan(appliedCurve.value(at: 0.5), 0.9)
        for progress in [0.25, 0.5, 0.75] {
            XCTAssertEqual(appliedCurve.value(at: progress),
                           StatusActionAnimationTiming.numberCurve.value(at: progress), accuracy: 0.00001)
        }
        XCTAssertEqual(label.layer.animation(forKey: "statusAction.numberFade")?.timingFunction, timing)
    }

    func testUIKitCountMovesUpWhenIncreasingAndDownWhenDecreasing() throws {
        for (before, after, increasing) in [(Int64(9), Int64(10), true), (10, 9, false)] {
            let view = StatusActionsUIView()
            view.backgroundColor = .white
            configure(view, actions: [animatedAction(liked: true, count: after)])
            let window = try show(view)
            defer { window.isHidden = true; window.rootViewController = nil }
            window.overrideUserInterfaceStyle = .light
            settle()
            view.layoutIfNeeded()
            let (_, label) = try content(view)
            let rect = label.convert(label.bounds, to: view)
            let restingY = try XCTUnwrap(actionNumberInkCenterY(image(view), in: rect))
            configure(view, actions: [animatedAction(count: before)])
            settle(milliseconds: 350)
            configure(view, actions: [animatedAction(liked: true, count: after)])
            // Inspect early travel as well as direction, so a weak or linear curve fails.
            for key in label.layer.animationKeys() ?? [] {
                let animation = try XCTUnwrap(label.layer.animation(forKey: key)?.copy() as? CAAnimation)
                animation.speed = 0
                animation.timeOffset = animation.duration * 0.25
                label.layer.add(animation, forKey: key)
            }
            settle()
            let moving = image(view)
            let attachment = XCTAttachment(image: moving)
            attachment.name = increasing ? "uikit-count-increasing" : "uikit-count-decreasing"
            attachment.lifetime = .keepAlways
            add(attachment)
            let enteringY = try XCTUnwrap(actionNumberInkCenterY(moving, in: rect))
            XCTAssertLessThan(abs(enteringY - restingY), Double(rect.height) * 0.25,
                              "By one quarter of the duration, the count should be close to its final position")
            if increasing {
                XCTAssertGreaterThan(enteringY, restingY + 0.5, "An increasing count must enter from below and move up")
            } else {
                XCTAssertLessThan(enteringY, restingY - 0.5, "A decreasing count must enter from above and move down")
            }
        }
    }

    func testUIKitGroupUpdatesAndHiddenCountsKeepMenuAndLayout() throws {
        let view = StatusActionsUIView()
        func group(_ liked: Bool, count: Int64) -> ActionMenu.Group {
            ActionMenu.Group(displayItem: animatedAction(liked: liked, count: count, key: "repost", family: .repost), actions: [animatedAction()])
        }
        configure(view, actions: [group(false, count: 9)])
        let window = try show(view)
        defer { window.isHidden = true; window.rootViewController = nil }
        let (icon, label) = try content(view)
        let height = view.timelineHeight(for: 320)
        configure(view, actions: [group(true, count: 10)])
        XCTAssertNotNil(icon.layer.animation(forKey: "statusAction.pop"))
        XCTAssertNotNil(label.layer.animation(forKey: "transition"))
        XCTAssertEqual(label.text, "10")
        XCTAssertTrue(try XCTUnwrap(view.subviews.first as? UIButton).showsMenuAsPrimaryAction)
        XCTAssertEqual(view.timelineHeight(for: 320), height)
        configure(view, actions: [group(true, count: 11)], showNumbers: false)
        XCTAssertTrue(label.isHidden)
        XCTAssertNil(label.layer.animationKeys())
    }

    private func state(post: String? = "post", position: Int = 0, liked: Bool = false, count: Int64 = 1,
                       key: String = "like", showNumbers: Bool = true, enabled: Bool = true) -> StatusActionAnimationState {
        let item = animatedAction(liked: liked, count: count, key: key, enabled: enabled)
        return StatusActionAnimationState(identity: .init(postKey: post, item: item, position: position), item: item, showNumbers: showNumbers)
    }

    private func configure(_ view: StatusActionsUIView, actions: [ActionMenu], post: String = "post", showNumbers: Bool = true) {
        view.configure(data: actions, useText: false, postActionStyle: .leftAligned, showNumbers: showNumbers, isDetail: false, postKey: post)
        view.layoutIfNeeded()
    }

    private func content(_ view: StatusActionsUIView) throws -> (UIImageView, UILabel) {
        let button = try XCTUnwrap(view.subviews.first as? UIButton)
        return (try XCTUnwrap(button.subviews.compactMap { $0 as? UIImageView }.first),
                try XCTUnwrap(button.subviews.compactMap { $0 as? UILabel }.first))
    }

    private func settle(milliseconds: Int = 80) {
        let settled = expectation(description: "Render a frame")
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(milliseconds)) { settled.fulfill() }
        wait(for: [settled], timeout: 2)
    }

    private func image(_ view: UIView) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: view.bounds.size, format: format).image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        }
    }

    private func show(_ view: UIView) throws -> UIWindow {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        let controller = UIViewController()
        window.rootViewController = controller
        view.frame = CGRect(x: 20, y: 80, width: 320, height: 44)
        controller.view.addSubview(view)
        window.isHidden = false
        controller.view.layoutIfNeeded()
        return window
    }
}

@MainActor
func animatedAction(liked: Bool = false, count: Int64 = 1, key: String = "like",
                    family: PostActionFamily? = .like, enabled: Bool = true) -> ActionMenu.Item {
    ActionMenu.Item(updateKey: key, icon: liked ? .unlike : .like, text: nil, count: UiNumber(value: count),
                    color: liked ? .red : .contentColor, clickEvent: ClickEventNoop.shared, actionFamily: family, enabled: enabled)
}

func actionNumberInkCenterY(_ image: UIImage, in rect: CGRect) -> Double? {
    guard let image = image.cgImage, let bytes = image.dataProvider?.data as Data? else { return nil }
    let stride = image.bitsPerPixel / 8
    var weight = 0.0
    var weightedY = 0.0
    for y in max(0, Int(rect.minY))..<min(image.height, Int(ceil(rect.maxY))) {
        for x in max(0, Int(rect.minX))..<min(image.width, Int(ceil(rect.maxX))) {
            let offset = y * image.bytesPerRow + x * stride
            let channels = bytes[offset..<offset + 3]
            let lowest = channels.min()!
            let range = Int(channels.max()!) - Int(lowest)
            guard lowest < 230, range > 40 else { continue }
            let ink = Double(255 - lowest)
            weight += ink
            weightedY += Double(y) * ink
        }
    }
    return weight > 0 ? weightedY / weight : nil
}
