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
        XCTAssertEqual(before.color, .contentColor, "Snapshots must preserve the outgoing content's rendering color")
        let implicitItem = ActionMenu.Item(updateKey: "like", icon: .like, text: nil, count: UiNumber(value: 1),
                                           color: nil, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
        let implicitDefault = StatusActionAnimationState(identity: before.identity, item: implicitItem, showNumbers: true)
        XCTAssertEqual(before.effects(from: implicitDefault, isVisible: true, reduceMotion: false).icon, .none)
        XCTAssertEqual(implicitDefault.effects(from: before, isVisible: true, reduceMotion: false).icon, .none)
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
        let slide = try XCTUnwrap(label.layer.animation(forKey: "statusAction.numberSlide") as? CASpringAnimation)
        XCTAssertGreaterThan(try XCTUnwrap(slide.fromValue as? Double), 0)
        XCTAssertEqual(slide.toValue as? Double, 0)
        pop.setValue("original", forKey: "testMarker")
        icon.layer.add(pop, forKey: "statusAction.pop")
        configure(view, actions: [animatedAction(liked: true, count: 2)])
        XCTAssertEqual(icon.layer.animation(forKey: "statusAction.pop")?.value(forKey: "testMarker") as? String, "original")
        configure(view, actions: [animatedAction()])
        XCTAssertNil(icon.layer.animation(forKey: "statusAction.pop"))
        XCTAssertNotNil(icon.layer.animation(forKey: "statusAction.iconFade"))
        let rollback = try XCTUnwrap(label.layer.animation(forKey: "statusAction.numberSlide") as? CASpringAnimation)
        XCTAssertLessThan(try XCTUnwrap(rollback.fromValue as? Double), 0)
    }

    func testUIKitIconUsesAndroidEnterAndExitMotion() throws {
        let view = StatusActionsUIView()
        configure(view, actions: [animatedAction(count: 9)])
        let window = try show(view)
        defer { window.isHidden = true; window.rootViewController = nil }
        configure(view, actions: [animatedAction(liked: true, count: 9)])
        let (icon, _) = try content(view)
        let scale = try XCTUnwrap(icon.layer.animation(forKey: "statusAction.pop") as? CASpringAnimation)
        XCTAssertEqual(scale.fromValue as? Double, 0)
        XCTAssertEqual(scale.stiffness, 400, accuracy: 0.0001)
        XCTAssertEqual(scale.damping, 20, accuracy: 0.0001)
        let fade = try XCTUnwrap(icon.layer.animation(forKey: "statusAction.iconFade") as? CASpringAnimation)
        XCTAssertEqual(fade.fromValue as? Double, 0)
        XCTAssertEqual(fade.toValue as? Double, 1)
        let images = try XCTUnwrap(view.subviews.first as? UIButton).subviews.compactMap { $0 as? UIImageView }
        XCTAssertEqual(images.count, 2, "The previous icon must shrink and fade while the new icon enters")
        let outgoing = try XCTUnwrap(images.first)
        XCTAssertNotNil(outgoing.layer.animation(forKey: "statusAction.pop"))
        XCTAssertNotNil(outgoing.layer.animation(forKey: "statusAction.iconFade"))
    }

    func testUIKitCountOvershootsBeforeSettling() throws {
        for (before, after, increasing) in [(Int64(9), Int64(10), true), (10, 9, false)] {
            let view = StatusActionsUIView()
            view.backgroundColor = .white
            configure(view, actions: [animatedAction(liked: true, count: after)])
            let window = try show(view)
            defer { window.isHidden = true; window.rootViewController = nil }
            window.overrideUserInterfaceStyle = .light
            settle()
            let (_, label) = try content(view)
            let rect = label.convert(label.bounds, to: view)
            let restingY = try XCTUnwrap(actionNumberInkCenterY(image(view), in: rect))
            configure(view, actions: [animatedAction(count: before)])
            settle(milliseconds: 700)
            configure(view, actions: [animatedAction(liked: true, count: after)])
            for key in label.layer.animationKeys() ?? [] {
                let animation = try XCTUnwrap(label.layer.animation(forKey: key)?.copy() as? CAAnimation)
                animation.speed = 0
                animation.timeOffset = 0.18
                label.layer.add(animation, forKey: key)
            }
            settle()
            let moving = image(view)
            let attachment = XCTAttachment(image: moving)
            attachment.name = increasing ? "uikit-count-up-rebound" : "uikit-count-down-rebound"
            attachment.lifetime = .keepAlways
            add(attachment)
            let reboundY = try XCTUnwrap(actionNumberInkCenterY(moving, in: rect))
            if increasing {
                XCTAssertLessThan(reboundY, restingY - 0.8, "An increasing count must overshoot above its resting position")
            } else {
                XCTAssertGreaterThan(reboundY, restingY + 0.8, "A decreasing count must overshoot below its resting position")
            }
        }
    }

    func testUIKitReuseReorderingAndRemovalClearRunningAnimations() throws {
        let view = StatusActionsUIView()
        configure(view, actions: [animatedAction(), animatedAction(key: "other", family: .bookmark)])
        let window = try show(view)
        defer { window.isHidden = true; window.rootViewController = nil }
        let button = try XCTUnwrap(view.subviews.first as? UIButton)
        XCTAssertEqual(button.subviews.compactMap { $0 as? UIImageView }.count, 1)
        XCTAssertEqual(button.subviews.compactMap { $0 as? UILabel }.count, 1)
        configure(view, actions: [animatedAction(liked: true, count: 2), animatedAction(key: "other", family: .bookmark)])
        let (icon, _) = try content(view)
        let images = button.subviews.compactMap { $0 as? UIImageView }
        let labels = button.subviews.compactMap { $0 as? UILabel }
        XCTAssertEqual(images.count, 2)
        XCTAssertEqual(labels.count, 2)
        func assertCleared(file: StaticString = #filePath, line: UInt = #line) {
            for content in button.subviews {
                XCTAssertNil(content.layer.animationKeys(), file: file, line: line)
            }
            XCTAssertNil(images.first?.image, file: file, line: line)
            XCTAssertNil(labels.first?.text, file: file, line: line)
        }
        XCTAssertNotNil(icon.layer.animation(forKey: "statusAction.pop"))
        configure(view, actions: [animatedAction(key: "other", family: .bookmark), animatedAction(liked: true, count: 2)])
        assertCleared()
        configure(view, actions: [animatedAction(count: 50)], post: "another")
        assertCleared()
        configure(view, actions: [animatedAction(liked: true, count: 51)], post: "another")
        XCTAssertNotNil(icon.layer.animation(forKey: "statusAction.pop"))
        view.removeFromSuperview()
        assertCleared()
        configure(view, actions: [animatedAction(count: 52)], post: "another")
        XCTAssertNil(icon.layer.animationKeys())
        window.rootViewController?.view.addSubview(view)
        configure(view, actions: [animatedAction(liked: true, count: 53)], post: "another")
        XCTAssertNotNil(icon.layer.animation(forKey: "statusAction.pop"))
        XCTAssertEqual(button.subviews.compactMap { $0 as? UIImageView }.count, 2)
        XCTAssertEqual(button.subviews.compactMap { $0 as? UILabel }.count, 2)
        view.prepareForPoolRemoval()
        assertCleared()
    }

    func testNumberSpringOvershootsAndMatchesBothRenderers() throws {
        let view = StatusActionsUIView()
        configure(view, actions: [animatedAction()])
        let window = try show(view)
        defer { window.isHidden = true; window.rootViewController = nil }
        configure(view, actions: [animatedAction(count: 2)])
        let (_, label) = try content(view)
        let slide = try XCTUnwrap(label.layer.animation(forKey: "statusAction.numberSlide") as? CASpringAnimation)
        let spring = StatusActionAnimationTiming.numberSpring
        XCTAssertEqual(slide.keyPath, "transform.translation.y")
        XCTAssertEqual(slide.mass, spring.mass)
        XCTAssertEqual(slide.stiffness, spring.stiffness)
        XCTAssertEqual(slide.damping, spring.damping)
        XCTAssertGreaterThan(spring.value(target: 1.0, time: 0.18), 1.1)
        XCTAssertEqual(spring.value(target: 1.0, time: 0.8), 1, accuracy: 0.01)
        let fade = try XCTUnwrap(label.layer.animation(forKey: "statusAction.numberFade") as? CASpringAnimation)
        XCTAssertGreaterThan(fade.damping, slide.damping, "Opacity must settle without bouncing the old text back in")
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
            // Sample before the spring reaches its first overshoot.
            for key in label.layer.animationKeys() ?? [] {
                let animation = try XCTUnwrap(label.layer.animation(forKey: key)?.copy() as? CAAnimation)
                animation.speed = 0
                animation.timeOffset = 0.10
                label.layer.add(animation, forKey: key)
            }
            settle()
            let moving = image(view)
            let attachment = XCTAttachment(image: moving)
            attachment.name = increasing ? "uikit-count-increasing" : "uikit-count-decreasing"
            attachment.lifetime = .keepAlways
            add(attachment)
            let enteringY = try XCTUnwrap(actionNumberInkCenterY(moving, in: rect))
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
        XCTAssertNotNil(label.layer.animation(forKey: "statusAction.numberSlide"))
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
        return (try XCTUnwrap(button.subviews.compactMap { $0 as? UIImageView }.last),
                try XCTUnwrap(button.subviews.compactMap { $0 as? UILabel }.last))
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
            guard lowest < 250, range > 12 else { continue }
            let ink = Double(255 - lowest)
            weight += ink
            weightedY += Double(y) * ink
        }
    }
    return weight > 0 ? weightedY / weight : nil
}
