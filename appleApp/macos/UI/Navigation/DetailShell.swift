import AppKit
import FlareAppleCore
import KotlinSharedUI
import SwiftUI

struct DetailShell: View {
    let destination: HomeTabsPresenterStateHomeTabs
    @Binding var selection: HomeTabsPresenterStateHomeTabs?
    @ObservedObject var homeTabsPresenter: KotlinPresenter<HomeTabsPresenterState>

    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            DetailTabView(
                tabs: normalizedTabs(tabs.cast(HomeTabsPresenterStateHomeTabs.self)),
                selection: tabSelection
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } errorContent: { _ in
            PlaceholderPanel(destination: selectedTab)
        } loadingContent: {
            PlaceholderPanel(destination: selectedTab)
                .redacted(reason: .placeholder)
        }
    }

    private var selectedTab: HomeTabsPresenterStateHomeTabs {
        selection ?? destination
    }

    private var tabSelection: Binding<String> {
        Binding {
            selectedTab.name
        } set: { name in
            selection = HomeTabsPresenterStateHomeTabs.macOSHomeTab(named: name)
        }
    }

    private func normalizedTabs(_ tabs: [HomeTabsPresenterStateHomeTabs]) -> [HomeTabsPresenterStateHomeTabs] {
        tabs.isEmpty ? [.home] : tabs
    }
}

private struct DetailTabView: NSViewRepresentable {
    let tabs: [HomeTabsPresenterStateHomeTabs]
    @Binding var selection: String

    func makeCoordinator() -> Coordinator {
        Coordinator(selection: $selection)
    }

    func makeNSView(context: Context) -> NSTabView {
        let tabView = NSTabView()
        tabView.tabViewType = .noTabsNoBorder
        tabView.drawsBackground = false
        tabView.delegate = context.coordinator

        syncTabViewItems(in: tabView)
        selectCurrentTab(in: tabView)
        return tabView
    }

    func updateNSView(_ tabView: NSTabView, context: Context) {
        tabView.tabViewType = .noTabsNoBorder
        tabView.drawsBackground = false
        context.coordinator.selection = $selection
        syncTabViewItems(in: tabView)
        selectCurrentTab(in: tabView)
    }

    private func syncTabViewItems(in tabView: NSTabView) {
        let currentIDs = tabView.tabViewItems.compactMap { $0.identifier as? String }
        let targetIDs = tabs.map(\.name)
        guard currentIDs != targetIDs else { return }

        for item in tabView.tabViewItems {
            tabView.removeTabViewItem(item)
        }

        for tab in tabs {
            tabView.addTabViewItem(makeTabViewItem(for: tab))
        }
    }

    private func makeTabViewItem(for tab: HomeTabsPresenterStateHomeTabs) -> NSTabViewItem {
        let item = NSTabViewItem(identifier: tab.name)
        item.label = tab.macOSTitle
        let hostingView = NSHostingView(rootView: content(for: tab))
        hostingView.autoresizingMask = [.width, .height]
        item.view = hostingView
        return item
    }

    private func content(for tab: HomeTabsPresenterStateHomeTabs) -> AnyView {
        AnyView(MacRouter(initialRoute: tab.macOSInitialRoute))
    }

    private func selectCurrentTab(in tabView: NSTabView) {
        guard let selectedID = validSelectionID else { return }

        if selection != selectedID {
            DispatchQueue.main.async {
                selection = selectedID
            }
        }

        if tabView.selectedTabViewItem?.identifier as? String != selectedID {
            tabView.selectTabViewItem(withIdentifier: selectedID)
        }
    }

    private var validSelectionID: String? {
        if tabs.contains(where: { $0.name == selection }) {
            selection
        } else {
            tabs.first?.name
        }
    }

    final class Coordinator: NSObject, NSTabViewDelegate {
        var selection: Binding<String>

        init(selection: Binding<String>) {
            self.selection = selection
        }

        func tabView(_ tabView: NSTabView, didSelect tabViewItem: NSTabViewItem?) {
            if let identifier = tabViewItem?.identifier as? String {
                selection.wrappedValue = identifier
            }
        }
    }
}
