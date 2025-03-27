import shared
import SwiftUI

struct TabContentViewSwiftUI: View {
    @ObservedObject var tabStore: AppBarTabSettingStore
    @Binding var selectedTab: String
    @State private var scrollPositions: [String: String] = [:]

    var body: some View {
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
    }
}
