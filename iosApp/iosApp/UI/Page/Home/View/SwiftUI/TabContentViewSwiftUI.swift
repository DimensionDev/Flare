import shared
import SwiftUI
import SwiftUIIntrospect

struct TabContentViewSwiftUI: View {
    @ObservedObject var tabStore: AppBarTabSettingStore
    @Binding var selectedTab: String
    // @Binding var tabScrollTriggers: [String: Bool]

    @Environment(FlareAppState.self) private var appState
    @Environment(FlareRouter.self) private var router
    // @EnvironmentObject private var timelineState: TimelineExtState

    var body: some View {
        ZStack {
            TabView(selection: $selectedTab) {
                ForEach(tabStore.availableAppBarTabsItems, id: \.key) { tab in
                    TimelineViewSwiftUI(
                        tab: tab,
                        store: tabStore,
                        isCurrentTab: selectedTab == tab.key
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
