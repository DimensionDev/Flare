import Combine
import SwiftUI
import UIKit
import XCTest

@MainActor
final class IPadCompactTabBarTests: XCTestCase {
    func testGroupedTabsSurviveIPadSizeClassChanges() async throws {
        guard #available(iOS 18.0, *) else { throw XCTSkip("Requires adaptable tabs") }
        for initialSizeClass in [UIUserInterfaceSizeClass.regular, .compact] {
            let fixture = try await Fixture(initialSizeClass: initialSizeClass)
            defer { fixture.close() }

            for _ in 0..<5 {
                await fixture.resize(to: .regular)
                XCTAssertEqual(try fixture.tabController().tabs.filter { $0 is UITabGroup }.count, 1)
                await fixture.resize(to: .compact)
                try fixture.assertCompactTabs()
            }
        }
    }

    func testPrimaryTabChangesUpdateTheCompactFilter() async throws {
        guard #available(iOS 18.0, *) else { throw XCTSkip("Requires adaptable tabs") }
        let fixture = try await Fixture()
        defer { fixture.close() }

        fixture.state.primaryTabs = ["home", "discover"]
        await fixture.settle()
        await fixture.resize(to: .compact)
        try fixture.assertCompactTabs()

        fixture.state.primaryTabs = ["notifications", "home", "discover"]
        await fixture.settle()
        try fixture.assertCompactTabs()
        await fixture.resize(to: .regular)
        await fixture.resize(to: .compact)
        try fixture.assertCompactTabs()
    }

    func testSidebarSelectionFallsBackToACompactTab() async throws {
        guard #available(iOS 18.0, *) else { throw XCTSkip("Requires adaptable tabs") }
        let fixture = try await Fixture()
        defer { fixture.close() }

        fixture.state.selection = "secondary:0"
        await fixture.settle()
        XCTAssertEqual(try fixture.tabController().selectedTab?.title, "Secondary 0")
        await fixture.resize(to: .compact)

        let controller = try fixture.tabController()
        let selectedTab = try XCTUnwrap(controller.selectedTab)
        XCTAssertTrue(fixture.state.primaryTabs.contains(selectedTab.title))
        try fixture.assertCompactTabs()
    }

    func testPhoneKeepsPrimaryTabsWithoutInstallingACompactFilter() async throws {
        guard #available(iOS 18.0, *) else { throw XCTSkip("Requires adaptable tabs") }
        let fixture = try await Fixture(idiom: .phone, initialSizeClass: .compact)
        defer { fixture.close() }

        for sizeClass in [UIUserInterfaceSizeClass.regular, .compact] {
            await fixture.resize(to: sizeClass)
            let controller = try fixture.tabController()
            XCTAssertEqual(controller.traitCollection.userInterfaceIdiom, .phone)
            XCTAssertEqual(controller.tabs.count, 3)
            XCTAssertNil(controller.compactTabIdentifiers)
        }
    }
}

@available(iOS 18.0, *)
@MainActor
private final class Fixture {
    let state = TabState()
    let host: UIHostingController<TestTabs>
    let window: UIWindow
    private let previousKeyWindow: UIWindow?

    init(idiom: UIUserInterfaceIdiom = .pad, initialSizeClass: UIUserInterfaceSizeClass = .regular) async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        host = UIHostingController(rootView: TestTabs(state: state, includesSidebar: idiom == .pad))
        host.traitOverrides.userInterfaceIdiom = idiom
        host.traitOverrides.horizontalSizeClass = initialSizeClass
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

    func resize(to sizeClass: UIUserInterfaceSizeClass) async {
        UIView.animate(withDuration: 0.08) {
            self.host.traitOverrides.horizontalSizeClass = sizeClass
            self.host.view.layoutIfNeeded()
        }
        await settle()
    }

    func settle() async {
        for _ in 0..<5 {
            window.layoutIfNeeded()
            try? await Task.sleep(for: .milliseconds(20))
        }
    }

    func tabController() throws -> UITabBarController {
        try XCTUnwrap(findTabController(host))
    }

    func assertCompactTabs(file: StaticString = #filePath, line: UInt = #line) throws {
        let controller = try tabController()
        let expected = state.primaryTabs.compactMap { title in
            controller.tabs.first(where: { $0.title == title })?.identifier
        }
        XCTAssertEqual(expected.count, state.primaryTabs.count, file: file, line: line)
        XCTAssertEqual(controller.compactTabIdentifiers, expected, file: file, line: line)
        XCTAssertEqual(controller.tabBar.items?.count, expected.count, file: file, line: line)
    }

    private func findTabController(_ controller: UIViewController) -> UITabBarController? {
        if let tabController = controller as? UITabBarController { return tabController }
        for child in controller.children {
            if let result = findTabController(child) { return result }
        }
        return nil
    }
}

@MainActor
private final class TabState: ObservableObject {
    @Published var primaryTabs = ["home", "notifications", "discover"]
    @Published var selection: String?
}

@available(iOS 18.0, *)
private struct TestTabs: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @ObservedObject var state: TabState
    let includesSidebar: Bool

    @ViewBuilder
    var body: some View {
        if #available(iOS 26.0, *) {
            tabs.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            tabs
        }
    }

    private var tabs: some View {
        TabView(selection: $state.selection) {
            ForEach(state.primaryTabs, id: \.self) { title in
                Tab(value: title, role: title == "discover" ? .search : nil) {
                    NavigationStack { Text(title) }
                } label: {
                    Label(title, systemImage: "house")
                }
            }
            if includesSidebar && sizeClass == .regular {
                ForEach(["Account"], id: \.self) { account in
                    TabSection {
                        ForEach(0..<4, id: \.self) { index in
                            Tab(value: "secondary:\(index)") {
                                NavigationStack { Text("Secondary \(index)") }
                            } label: {
                                Label("Secondary \(index)", systemImage: "person")
                            }
                            .tabPlacement(.sidebarOnly)
                        }
                    } header: {
                        Label(account, systemImage: "person.circle")
                    }
                    .tabPlacement(.sidebarOnly)
                }
                Tab("Settings", systemImage: "gear", value: "settings") {
                    Text("Settings")
                }
                .tabPlacement(.sidebarOnly)
            }
        }
        .modifier(IPadCompactTabBarModifier(primaryTabIDs: state.primaryTabs))
        .tabViewStyle(.sidebarAdaptable)
    }
}
