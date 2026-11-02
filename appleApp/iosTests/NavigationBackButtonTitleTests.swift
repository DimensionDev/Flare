import Combine
import SwiftUI
import UIKit
import XCTest

@MainActor
final class NavigationBackButtonTitleTests: XCTestCase {
    func testHistoryTitleDoesNotChangeVisibleTitlesOrLayout() async throws {
        for emptyTitle in [false, true] {
            let fixture = try await NavigationTitleFixture(emptyTitle: emptyTitle)
            defer { fixture.close() }
            let navigation = try fixture.navigationController()
            let item = try XCTUnwrap(navigation.topViewController?.navigationItem)
            let originalTitle = item.title
            let originalTitleView = item.titleView
            let originalBarFrame = navigation.navigationBar.frame
            let originalBodyFrame = try fixture.bodyFrame()
            let originalDisplayMode = item.backButtonDisplayMode

            fixture.state.rootTitle = "Timeline"
            await fixture.settle()

            XCTAssertEqual(item.backButtonTitle, "Timeline")
            XCTAssertEqual(item.title, originalTitle)
            XCTAssertTrue(item.titleView === originalTitleView)
            XCTAssertEqual(item.backButtonDisplayMode, originalDisplayMode)
            XCTAssertEqual(navigation.navigationBar.frame, originalBarFrame)
            XCTAssertEqual(try fixture.bodyFrame(), originalBodyFrame)
        }
    }

    func testAsyncUpdatesBelongToTheirSourcePageAcrossPushAndPop() async throws {
        let fixture = try await NavigationTitleFixture()
        defer { fixture.close() }
        fixture.state.rootTitle = "Home"
        await fixture.settle()
        let navigation = try fixture.navigationController()
        let root = try XCTUnwrap(navigation.topViewController)

        fixture.state.path = [1]
        await fixture.settle()
        let profile = try XCTUnwrap(navigation.topViewController)
        XCTAssertFalse(root === profile)
        XCTAssertEqual(profile.navigationItem.backButtonTitle, "Profile")

        fixture.state.rootTitle = "Following"
        fixture.state.profileTitle = "Alice"
        await fixture.settle()
        XCTAssertEqual(root.navigationItem.backButtonTitle, "Following")
        XCTAssertEqual(profile.navigationItem.backButtonTitle, "Alice")

        fixture.state.path = [1, 2]
        await fixture.settle()
        let detail = try XCTUnwrap(navigation.topViewController)
        XCTAssertEqual(detail.navigationItem.title, "Detail")
        XCTAssertNil(detail.navigationItem.backButtonTitle)
        fixture.state.profileTitle = "Alice updated"
        await fixture.settle()
        XCTAssertNil(detail.navigationItem.backButtonTitle)

        // SwiftUI can defer updates to a covered destination until it reappears.
        fixture.state.path = [1]
        await fixture.settle()
        XCTAssertTrue(navigation.topViewController === profile)
        XCTAssertEqual(profile.navigationItem.backButtonTitle, "Alice updated")

        fixture.state.path = []
        await fixture.settle()
        XCTAssertTrue(navigation.topViewController === root)
        XCTAssertEqual(root.navigationItem.backButtonTitle, "Following")
        XCTAssertNotEqual(root.navigationItem.title, "Following")
    }

    func testEmbeddedControllerDoesNotReceiveTheScreenHistoryTitle() async throws {
        let fixture = try await NavigationTitleFixture()
        defer { fixture.close() }
        fixture.state.rootTitle = "Home"
        await fixture.settle()
        let navigation = try fixture.navigationController()
        XCTAssertEqual(navigation.topViewController?.navigationItem.backButtonTitle, "Home")
        XCTAssertNil(fixture.state.embedded.navigationItem.backButtonTitle)
    }
}

@MainActor
private final class NavigationTitleState: ObservableObject {
    @Published var path: [Int] = []
    @Published var rootTitle = ""
    @Published var profileTitle = "Profile"
    let embedded = UIViewController()
}

@MainActor
private final class NavigationTitleFixture {
    let state = NavigationTitleState()
    let window: UIWindow
    let host: UIHostingController<NavigationTitleRoot>
    private let previousKeyWindow: UIWindow?

    init(emptyTitle: Bool = false) async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        host = UIHostingController(rootView: NavigationTitleRoot(state: state, emptyTitle: emptyTitle))
        window = UIWindow(windowScene: scene)
        window.rootViewController = host
        window.makeKeyAndVisible()
        await settle()
    }

    func close() {
        window.isHidden = true
        window.rootViewController = nil
        previousKeyWindow?.makeKey()
    }

    func settle() async {
        for _ in 0..<10 {
            window.layoutIfNeeded()
            try? await Task.sleep(for: .milliseconds(50))
        }
    }

    func navigationController() throws -> UINavigationController {
        func find(_ controller: UIViewController) -> UINavigationController? {
            if let navigation = controller as? UINavigationController { return navigation }
            return controller.children.lazy.compactMap(find).first
        }
        return try XCTUnwrap(find(host))
    }

    func bodyFrame() throws -> CGRect {
        let view = try XCTUnwrap(state.embedded.viewIfLoaded)
        return view.convert(view.bounds, to: window)
    }
}

private struct NavigationTitleRoot: View {
    @ObservedObject var state: NavigationTitleState
    let emptyTitle: Bool

    var body: some View {
        NavigationStack(path: $state.path) {
            Group {
                if emptyTitle { root.navigationTitle("") } else { root }
            }
            .navigationBackButtonTitle(state.rootTitle)
            .navigationDestination(for: Int.self) { destination in
                if destination == 1 {
                    NavigationTitleProfile(state: state)
                } else {
                    Text("Detail content").navigationTitle("Detail")
                }
            }
        }
    }

    private var root: some View {
        NavigationTitleEmbeddedController(controller: state.embedded)
            .toolbar { ToolbarItem(placement: .principal) { Text("Visible root") } }
    }
}

private struct NavigationTitleProfile: View {
    @ObservedObject var state: NavigationTitleState

    var body: some View {
        Text("Profile content")
            .toolbar { ToolbarItem(placement: .principal) { Text("Visible profile") } }
            .navigationBarTitleDisplayMode(.inline)
            .navigationBackButtonTitle(state.profileTitle)
    }
}

private struct NavigationTitleEmbeddedController: UIViewControllerRepresentable {
    let controller: UIViewController
    func makeUIViewController(context: Context) -> UIViewController { controller }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
