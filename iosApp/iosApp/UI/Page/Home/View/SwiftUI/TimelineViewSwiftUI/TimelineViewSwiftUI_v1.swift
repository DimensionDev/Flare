import os
import shared
import SwiftUI

 
 struct TimelineItemWrapper: Identifiable, Equatable {
    let index: Int
    let stableID: String
    let itemKey: String
    let lastUpdateTime: Date
    
     var id: String { stableID }
    
     static func == (lhs: TimelineItemWrapper, rhs: TimelineItemWrapper) -> Bool {
        lhs.stableID == rhs.stableID &&
        lhs.index == rhs.index &&
        lhs.lastUpdateTime == rhs.lastUpdateTime
    }
}


 struct TimelineViewSwiftUIV1: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @State private var presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool
    @Binding var showFloatingButton: Bool
    
 
    @State private var timelineItems: [TimelineItemWrapper] = []
    @State private var lastKnownItemCount: Int32 = 0
    @State private var isUpdatingItems = false
    
     private func generateStableID(for index: Int, itemKey: String?) -> String {
        if let itemKey, !itemKey.isEmpty {
            "timeline_item_\(itemKey)"
        } else {
            "timeline_placeholder_\(index)"
        }
    }
    
     private func updateTimelineItemsIfNeeded(success: PagingStateSuccess<UiTimeline>) {
        FlareLog.debug("TimelineViewSwiftUI_v1 updateTimelineItemsIfNeeded called")
        
        guard !isUpdatingItems else {
            FlareLog.debug("TimelineViewSwiftUI_v1 Already updating items, returning")
            return
        }
        
        let currentItemCount = success.itemCount
        guard currentItemCount != lastKnownItemCount else {
            FlareLog.debug("TimelineViewSwiftUI_v1 Item count unchanged, returning")
            return
        }
        
        FlareLog.debug("TimelineViewSwiftUI_v1 Starting update process")
        isUpdatingItems = true
        
        var newItems: [TimelineItemWrapper] = []
        let newItemCount = Int(currentItemCount)
        
        if lastKnownItemCount == 0 || timelineItems.isEmpty {
            FlareLog.debug("TimelineViewSwiftUI_v1 First load detected, creating all items")
            for index in 0 ..< newItemCount {
                let status = success.peek(index: Int32(index))
                let itemKey = status?.itemKey ?? ""
                let stableID = generateStableID(for: index, itemKey: itemKey)
                
                let wrapper = TimelineItemWrapper(
                    index: index,
                    stableID: stableID,
                    itemKey: itemKey,
                    lastUpdateTime: Date()
                )
                newItems.append(wrapper)
            }
        } else {
            FlareLog.debug("TimelineViewSwiftUI_v1 Incremental update detected")
            let oldItemCount = Int(lastKnownItemCount)
            
            for index in 0 ..< min(oldItemCount, newItemCount) {
                if index < timelineItems.count {
                    var existingItem = timelineItems[index]
                    existingItem = TimelineItemWrapper(
                        index: index,
                        stableID: existingItem.stableID,
                        itemKey: existingItem.itemKey,
                        lastUpdateTime: existingItem.lastUpdateTime
                    )
                    newItems.append(existingItem)
                }
            }
            
            for index in oldItemCount ..< newItemCount {
                let status = success.peek(index: Int32(index))
                let itemKey = status?.itemKey ?? ""
                let stableID = generateStableID(for: index, itemKey: itemKey)
                
                let wrapper = TimelineItemWrapper(
                    index: index,
                    stableID: stableID,
                    itemKey: itemKey,
                    lastUpdateTime: Date()
                )
                newItems.append(wrapper)
            }
        }
        
        DispatchQueue.main.async {
            withAnimation(.none) {
                timelineItems = newItems
                lastKnownItemCount = currentItemCount
                FlareLog.debug("TimelineViewSwiftUI_v1 Update completed. timelineItems.count: \(timelineItems.count)")
            }
        }
        
        isUpdatingItems = false
        FlareLog.debug("TimelineViewSwiftUI_v1 updateTimelineItemsIfNeeded finished")
    }
    
    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ScrollToTopView(tabKey: tab.key)
                        .id("top")
                        .background(
                            GeometryReader { geometry in
                                Color.clear
                                    .preference(key: ScrollOffsetPreferenceKey.self,
                                                value: geometry.frame(in: .global).minY)
                            }
                        )
                    
                    if let presenter {
                        ObservePresenter(presenter: presenter) { state in
                            if let timelineState = state as? TimelineState,
                               case let .success(success) = onEnum(of: timelineState.listState)
                            {
                                let _ = {
                                    let _ = FlareLog.debug("TimelineViewSwiftUI_1.1 ObservePresenter success state detected")
                                    updateTimelineItemsIfNeeded(success: success)
                                }()

                                let _ = FlareLog.debug("TimelineViewSwiftUI_1.1 Rendering \(timelineItems.count) items with Stable ID")
                                
                                ForEach(timelineItems, id: \.stableID) { item in
                                    renderTimelineItem(item: item, success: success)
                                }
                                
                            } else if let timelineState = state as? TimelineState {
                                StatusTimelineComponent(
                                    data: timelineState.listState,
                                    detailKey: nil
                                )
                                .padding(.horizontal, 16)
                            }
                        }
                    }
                }
            }
            .onChange(of: scrollToTopTrigger) { _, _ in
                let _ = FlareLog.debug("TimelineView_1.1 ScrollToTop trigger changed for tab: \(tab.key)")
                guard isCurrentTab else { return }
                
                withAnimation(.easeInOut(duration: 0.5)) {
                    proxy.scrollTo(ScrollToTopView.Constants.scrollToTop, anchor: .top)
                }
            }
        }
        .scrollPosition(id: $scrollPositionID)
        .refreshable {
            if let presenter,
               let timelineState = presenter.models.value as? TimelineState
            {
                try? await timelineState.refresh()
            }
        }
        .task {
            if presenter == nil {
                presenter = store.getOrCreatePresenter(for: tab)
            }
        }
        .coordinateSpace(name: "scroll")
        .onPreferenceChange(ScrollOffsetPreferenceKey.self) { offset in
            handleScrollOffsetChange(offset)
        }
    }
    
    private func handleScrollOffsetChange(_ offset: CGFloat) {
        guard isCurrentTab else { return }
        handleFloatingButtonVisibility(offset)
    }
    
    private func handleFloatingButtonVisibility(_: CGFloat) {
        if !showFloatingButton {
            showFloatingButton = true
        }
    }
    
    @ViewBuilder
    private func renderTimelineItem(item: TimelineItemWrapper, success: PagingStateSuccess<UiTimeline>) -> some View {
        let index = item.index
        
        if let status = success.peek(index: Int32(index)) {
            let statusID = status.itemKey
            StatusItemView(data: status, detailKey: nil)
                .padding(.vertical, 8)
                .padding(.horizontal, 16)
                .id("StatusItemView_\(item.stableID)")
                .onAppear {
                    if index > success.itemCount - 4 {
                        success.get(index: Int32(index))
                    }
                }
                .background(
                    GeometryReader { _ in
                        Color.clear
                            .onAppear {
                                if index == 0 {
                                    scrollPositionID = statusID
                                }
                            }
                    }
                )
        } else {
            StatusPlaceHolder()
                .onAppear {
                    success.get(index: Int32(index))
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 16)
        }
        
        if index < timelineItems.count - 1 {
            Divider()
                .padding(.horizontal, 16)
        }
    }
}
