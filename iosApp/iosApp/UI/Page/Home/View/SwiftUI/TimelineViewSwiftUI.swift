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

    var body: some View {
        Group {
            switch versionManager.currentVersion {
            case .base:
                TimelineViewSwiftUIBase(
                    tab: tab,
                    store: store,
                    scrollPositionID: $scrollPositionID,
                    scrollToTopTrigger: $scrollToTopTrigger,
                    isCurrentTab: isCurrentTab,
                    showFloatingButton: $showFloatingButton
                )
            case .v1_1:
                TimelineViewSwiftUIV1(
                    tab: tab,
                    store: store,
                    scrollPositionID: $scrollPositionID,
                    scrollToTopTrigger: $scrollToTopTrigger,
                    isCurrentTab: isCurrentTab,
                    showFloatingButton: $showFloatingButton
                )
            case .v2_0:
                    TimelineViewSwiftUIV2(
                        tab: tab,
                        store: store,
                        scrollPositionID: $scrollPositionID,
                        scrollToTopTrigger: $scrollToTopTrigger,
                        isCurrentTab: isCurrentTab,
                        showFloatingButton: $showFloatingButton
                )
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .timelineVersionChanged)) { _ in
            print("🔄 [TimelineViewSwiftUI] Received version change notification")
        }
    }
}

