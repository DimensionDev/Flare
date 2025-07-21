import shared
import SwiftUI
import SwiftUIIntrospect

struct TabContentViewSwiftUI: View {
    @ObservedObject var tabStore: AppBarTabSettingStore
    @Binding var selectedTab: String
    @Environment(\.appSettings) private var appSettings

    var body: some View {
        let displayType: TimelineDisplayType = appSettings.appearanceSettings.timelineDisplayType

        TabView(selection: $selectedTab) {
            ForEach(tabStore.availableAppBarTabsItems, id: \.key) { tab in
                switch displayType {
                case .timeline:
                    TimelineViewSwiftUIV4(
                        tab: tab,
                        store: tabStore,
                        isCurrentTab: selectedTab == tab.key
                    ).tag(tab.key)

                case .mediaWaterfall, .mediaCardWaterfall:
                    WaterfallView(
                        tab: tab,
                        store: tabStore,
                        isCurrentTab: selectedTab == tab.key,
                        displayType: displayType
                    ).tag(tab.key)
                }
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
