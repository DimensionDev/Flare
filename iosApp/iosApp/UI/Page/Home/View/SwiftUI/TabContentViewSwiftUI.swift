import shared
import SwiftUI
import SwiftUIIntrospect

struct TabContentViewSwiftUI: View {
    @ObservedObject var tabStore: AppBarTabSettingStore
    @Binding var selectedTab: String
    @Binding var tabScrollTriggers: [String: Bool]
    @Binding var showFloatingButton: Bool

    @State private var scrollPositions: [String: String] = [:]
    @Environment(FlareAppState.self) private var appState
    @Environment(FlareRouter.self) private var router

    var body: some View {
        ZStack {
            TabView(selection: $selectedTab) {
                ForEach(tabStore.availableAppBarTabsItems, id: \.key) { tab in
                    TimelineViewSwiftUI(
                        tab: tab,
                        store: tabStore,
                        scrollPositionID: Binding(
                            get: { scrollPositions[tab.key] },
                            set: { newValue in
                                if let newValue {
                                    scrollPositions[tab.key] = newValue
                                }
                            }
                        ),
                        scrollToTopTrigger: Binding(
                            get: { tabScrollTriggers[tab.key] ?? false },
                            set: { newValue in
                                tabScrollTriggers[tab.key] = newValue
                            }
                        ),
                        isCurrentTab: selectedTab == tab.key,
                        showFloatingButton: $showFloatingButton
                    )
                    .tag(tab.key)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .introspect(
                .tabView(style: .page),
                on: .iOS(.v17, .v18)
            ) { collectionView in
                collectionView.bounces = false
            }
        }
    }
}
