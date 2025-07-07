import os
import shared
import SwiftUI

struct TimelineViewSwiftUI: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool
    @Binding var showFloatingButton: Bool

    @State private var versionManager = TimelineVersionManager.shared
    @Environment(\.appSettings) private var appSettings

    var body: some View {
        let displayType: TimelineDisplayType = appSettings.appearanceSettings.timelineDisplayType

        Group {
            switch (versionManager.currentVersion, displayType) {
            case (.v3_0, .timeline):
                TimelineViewSwiftUIV3(
                    tab: tab,
                    store: store,
                    scrollPositionID: $scrollPositionID,
                    scrollToTopTrigger: $scrollToTopTrigger,
                    isCurrentTab: isCurrentTab,
                    showFloatingButton: $showFloatingButton
                )

            case (.v4_0, .timeline):
                TimelineViewSwiftUIV4(
                    tab: tab,
                    store: store,
                    scrollToTopTrigger: $scrollToTopTrigger,
                    isCurrentTab: isCurrentTab,
                    showFloatingButton: $showFloatingButton
                )

            case (_, .mediaWaterfall), (_, .mediaCardWaterfall):
                WaterfallView(
                    tab: tab,
                    store: store,
                    scrollPositionID: $scrollPositionID,
                    scrollToTopTrigger: $scrollToTopTrigger,
                    isCurrentTab: isCurrentTab,
                    showFloatingButton: $showFloatingButton,
                    displayType: displayType
                )

            case (.base, .timeline):
                TimelineViewSwiftUIBase(
                    tab: tab,
                    store: store,
                    scrollPositionID: $scrollPositionID,
                    scrollToTopTrigger: $scrollToTopTrigger,
                    isCurrentTab: isCurrentTab,
                    showFloatingButton: $showFloatingButton
                )

            case (.v1_1, .timeline):
                TimelineViewSwiftUIV1(
                    tab: tab,
                    store: store,
                    scrollPositionID: $scrollPositionID,
                    scrollToTopTrigger: $scrollToTopTrigger,
                    isCurrentTab: isCurrentTab,
                    showFloatingButton: $showFloatingButton
                )

            case (.v2_0, .timeline):
                TimelineViewSwiftUIV2(
                    tab: tab,
                    store: store,
                    scrollPositionID: $scrollPositionID,
                    scrollToTopTrigger: $scrollToTopTrigger,
                    isCurrentTab: isCurrentTab,
                    showFloatingButton: $showFloatingButton
                )

            default:
                TimelineViewSwiftUIV4(
                    tab: tab,
                    store: store,
                    scrollToTopTrigger: $scrollToTopTrigger,
                    isCurrentTab: isCurrentTab,
                    showFloatingButton: $showFloatingButton
                )
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .timelineVersionChanged)) { _ in
            FlareLog.debug("TimelineViewSwiftUI Received version change notification")
        }
    }
}
