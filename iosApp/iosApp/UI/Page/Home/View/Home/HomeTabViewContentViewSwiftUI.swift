import shared
import SwiftUI
import SwiftUIIntrospect

struct HomeTabViewContentViewSwiftUI: View {
    var tabStore: AppBarTabSettingStore
    @Binding var selectedTab: String
    @Environment(\.appSettings) private var appSettings

    var body: some View {
        let displayType: TimelineDisplayType = appSettings.appearanceSettings.timelineDisplayType

        let _ = FlareLog.debug("🔍 [HomeTabView] Current selectedTab: '\(selectedTab)'")

        TabView(selection: $selectedTab) {
            ForEach(tabStore.availableAppBarTabsItems, id: \.key) { tab in
                let isCurrentAppBarTabSelected = selectedTab == tab.key
                let _ = FlareLog.debug("🔍 [HomeTabView] Tab '\(tab.key)': selectedTab==tab.key -> \(isCurrentAppBarTabSelected)")

                switch displayType {
                case .timeline:
                    TimelineViewSwiftUIV4(
                        tab: tab,
                        store: tabStore,
                        isCurrentAppBarTabSelected: isCurrentAppBarTabSelected
                    ).tag(tab.key)

                case .mediaWaterfall, .mediaCardWaterfall:
                    WaterfallView(
                        tab: tab,
                        store: tabStore,
                        isCurrentAppBarTabSelected: isCurrentAppBarTabSelected,
                        displayType: displayType
                    ).tag(tab.key)
                }
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .onChange(of: selectedTab) { oldValue, newValue in
            FlareLog.debug("📱 [HomeTabView] TabView selectedTab changed: '\(oldValue)' → '\(newValue)'")
        }
        .introspect(
            .tabView(style: .page),
            on: .iOS(.v17, .v18)
        ) { collectionView in
            collectionView.bounces = false
        }
    }
}
