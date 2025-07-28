import shared
import SwiftUI
import SwiftUIIntrospect

struct HomeTabViewContentViewSwiftUI: View {
    @ObservedObject var tabStore: AppBarTabSettingStore
    @Binding var selectedTab: String
    @Environment(\.appSettings) private var appSettings

    var body: some View {
        let displayType: TimelineDisplayType = appSettings.appearanceSettings.timelineDisplayType

        // 添加selectedTab变化监控
        let _ = FlareLog.debug("🔍 [HomeTabView] Current selectedTab: '\(selectedTab)'")

        TabView(selection: $selectedTab) {
            ForEach(tabStore.availableAppBarTabsItems, id: \.key) { tab in
                // 添加详细的isCurrentTab计算日志
                let isCurrentTabValue = selectedTab == tab.key
                let _ = FlareLog.debug("🔍 [HomeTabView] Tab '\(tab.key)': selectedTab==tab.key -> \(isCurrentTabValue)")

                switch displayType {
                case .timeline:
                    TimelineViewSwiftUIV4(
                        tab: tab,
                        store: tabStore,
                        isCurrentTab: isCurrentTabValue
                    ).tag(tab.key)

                case .mediaWaterfall, .mediaCardWaterfall:
                    WaterfallView(
                        tab: tab,
                        store: tabStore,
                        isCurrentTab: isCurrentTabValue,
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
