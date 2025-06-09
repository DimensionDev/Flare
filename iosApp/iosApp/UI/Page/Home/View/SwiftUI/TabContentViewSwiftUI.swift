import shared
import SwiftUI
import SwiftUIIntrospect

struct TabContentViewSwiftUI: View {
    @ObservedObject var tabStore: AppBarTabSettingStore
    @Binding var selectedTab: String
    @State private var scrollPositions: [String: String] = [:]
    @EnvironmentObject private var appState: FlareAppState
    @EnvironmentObject private var router: FlareRouter

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
                        )
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
