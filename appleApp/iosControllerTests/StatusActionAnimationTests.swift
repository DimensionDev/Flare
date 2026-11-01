import XCTest
import UIKit
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
        let pop = try XCTUnwrap(icon.layer.animation(forKey: "statusAction.pop") as? CAKeyframeAnimation)
        XCTAssertEqual(pop.duration, 0.25)
        XCTAssertEqual(label.text, "2")
        XCTAssertEqual((view.subviews.first as? UIButton)?.accessibilityValue, "2")
        let transition = try XCTUnwrap(label.layer.animation(forKey: "transition") as? CATransition)
        XCTAssertEqual(transition.subtype, .fromBottom)
        pop.setValue("original", forKey: "testMarker")
        icon.layer.add(pop, forKey: "statusAction.pop")
        configure(view, actions: [animatedAction(liked: true, count: 2)])
        XCTAssertEqual(icon.layer.animation(forKey: "statusAction.pop")?.value(forKey: "testMarker") as? String, "original")
        configure(view, actions: [animatedAction()])
        XCTAssertNil(icon.layer.animation(forKey: "statusAction.pop"))
        XCTAssertNotNil(icon.layer.animation(forKey: "transition"))
        XCTAssertEqual((label.layer.animation(forKey: "transition") as? CATransition)?.subtype, .fromTop)
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
