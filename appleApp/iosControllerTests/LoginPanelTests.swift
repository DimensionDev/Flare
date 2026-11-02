import XCTest
import SwiftUI
import UIKit
import FlareAppleCore
import KotlinSharedUI
@testable import Flare

@MainActor
final class LoginPanelTests: XCTestCase {
    private var window: UIWindow!
    private var previousWindow: UIWindow?
    private let target = ReloginTarget(
        accountKey: MicroBlogKey(id: "login-panel-test", host: "bsky.social"),
        platformId: "Bluesky"
    )

    override func setUp() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        previousWindow = scene.windows.first(where: \.isKeyWindow)
        window = UIWindow(windowScene: scene)
        window.rootViewController = UIViewController()
        window.makeKeyAndVisible()
    }

    override func tearDown() async throws {
        window.isHidden = true
        window.rootViewController = nil
        window = nil
        previousWindow?.makeKey()
    }

    func testSharedPanelSwitchesMethodsAndPreservesStateOnlyWithinTheSameFlow() async throws {
        let login = KotlinPresenter(presenter: ServiceSelectPresenter(toHome: {}))
        let relogin = KotlinPresenter(presenter: ReloginPresenter(target: self.target, onSuccess: {}))
        let selection = Selection()
        var createdMethods: [LoginMethodType] = []
        func panel(host: String = "bsky.social") -> LoginPanel {
            LoginPanel(
                methods: relogin.state.methods,
                selectedMethod: Binding(get: { selection.method }, set: { selection.method = $0 }),
                flowId: "Bluesky-\(host)",
                agreementUrl: nil
            ) { method in
                createdMethods.append(method)
                return login.state.createLoginHandler(platformId: "Bluesky", host: host, methodType: method, redirectUri: nil)
            }
        }
        let controller = UIHostingController(rootView: panel())
        window.rootViewController = controller
        try await eventually { self.fields.contains(where: \.isSecureTextEntry) }
        let username = try XCTUnwrap(fields.first { !$0.isSecureTextEntry })
        username.text = "entered-user"
        username.sendActions(for: .editingChanged)
        controller.rootView = panel()
        try await Task.sleep(for: .milliseconds(300))
        XCTAssertEqual(fields.first { !$0.isSecureTextEntry }?.text, "entered-user")
        XCTAssertEqual(createdMethods, [.password])

        let picker = try XCTUnwrap(descendants(window).compactMap { $0 as? UISegmentedControl }.first)
        picker.selectedSegmentIndex = 1
        picker.sendActions(for: .valueChanged)
        try await eventually { createdMethods == [.password, .oauth] && self.fields.count == 1 && !self.fields.contains(where: \.isSecureTextEntry) }
        XCTAssertEqual(selection.method, .oauth)
        XCTAssertTrue(picker.isDescendant(of: window), "Switching methods must retain the picker")

        picker.selectedSegmentIndex = 0
        picker.sendActions(for: .valueChanged)
        try await eventually { createdMethods == [.password, .oauth, .password] && self.fields.count == 2 && self.fields.contains(where: \.isSecureTextEntry) }
        XCTAssertTrue(fields.allSatisfy { ($0.text ?? "").isEmpty }, "Switching back must create a fresh form")

        let nextUsername = try XCTUnwrap(fields.first { !$0.isSecureTextEntry })
        nextUsername.text = "another-user"
        nextUsername.sendActions(for: .editingChanged)
        controller.rootView = panel(host: "another.example")
        try await eventually { createdMethods.count == 4 && self.fields.allSatisfy { ($0.text ?? "").isEmpty } }
    }

    func testReloginScreenSwitchesMethodsWithAFreshForm() async throws {
        window.rootViewController = UIHostingController(rootView: ReloginScreen(target: target, toHome: {}))
        try await eventually { self.fields.contains(where: \.isSecureTextEntry) }
        let username = try XCTUnwrap(fields.first { !$0.isSecureTextEntry })
        username.text = "previous-input"
        username.sendActions(for: .editingChanged)
        let picker = try XCTUnwrap(descendants(window).compactMap { $0 as? UISegmentedControl }.first)
        picker.selectedSegmentIndex = 1
        picker.sendActions(for: .valueChanged)
        try await eventually { self.fields.count == 1 && !self.fields.contains(where: \.isSecureTextEntry) }
        XCTAssertTrue(picker.isDescendant(of: window))
        picker.selectedSegmentIndex = 0
        picker.sendActions(for: .valueChanged)
        try await eventually { self.fields.count == 2 && self.fields.contains(where: \.isSecureTextEntry) }
        XCTAssertTrue(fields.allSatisfy { ($0.text ?? "").isEmpty })
    }

    func testReloginSuccessCallbackDismissesPresentedSheetAndPreservesParent() async throws {
        for backStack in [[], [Route.serviceSelect]] {
            let navigation = RouterNavigationState<Route>()
            navigation.backStack = backStack
            let screen = ReloginScreen(target: target, toHome: navigation.dismissSheet)
            let controller = UIHostingController(rootView: SheetHost(navigation: navigation, screen: screen))
            window.rootViewController = controller
            try await Task.sleep(for: .milliseconds(100))
            navigation.sheet = .relogin(target.accountKey, target.platformId)
            try await eventually { controller.presentedViewController != nil && self.fields.contains(where: \.isSecureTextEntry) }

            screen.toHome()
            try await eventually { controller.presentedViewController == nil }
            screen.toHome()
            XCTAssertNil(navigation.sheet)
            XCTAssertEqual(navigation.backStack, backStack)
        }
    }

    private var fields: [UITextField] {
        descendants(window).compactMap { $0 as? UITextField }
    }

    private func descendants(_ view: UIView) -> [UIView] {
        [view] + view.subviews.flatMap(descendants)
    }

    private func eventually(_ predicate: () -> Bool) async throws {
        let deadline = Date().addingTimeInterval(5)
        while !predicate(), Date() < deadline {
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTAssertTrue(predicate())
    }

    @Observable
    final class Selection {
        var method: LoginMethodType?
    }

    private struct SheetHost: View {
        @Bindable var navigation: RouterNavigationState<Route>
        let screen: ReloginScreen

        var body: some View {
            NavigationStack(path: $navigation.backStack) {
                Text("Root")
                    .navigationDestination(for: Route.self) { _ in
                        Text("Parent")
                    }
            }
            .sheet(item: $navigation.sheet) { _ in
                NavigationStack { screen }
            }
        }
    }
}
