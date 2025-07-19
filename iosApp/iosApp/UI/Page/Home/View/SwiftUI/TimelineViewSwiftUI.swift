import os
import shared
import SwiftUI

struct TimelineViewSwiftUI: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    let isCurrentTab: Bool
    @Environment(\.appSettings) private var appSettings

    var body: some View {
        let displayType: TimelineDisplayType = appSettings.appearanceSettings.timelineDisplayType
        switch displayType {
        case .timeline:
            TimelineViewSwiftUIV4(
                tab: tab,
                store: store,
                isCurrentTab: isCurrentTab
            )

        case .mediaWaterfall, .mediaCardWaterfall:
            WaterfallView(
                tab: tab,
                store: store,
                isCurrentTab: isCurrentTab,
                displayType: displayType
            )
        }
    }
}
