import os
import shared
import SwiftUI

//   Timeline 1.1 Version (Stable ID Only)

struct TimelineViewSwiftUI_1_1_Standalone: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @State private var presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool

    @Binding var showFloatingButton: Bool

    //  Timeline Items State for Stable ID System (1.1 Optimization)

    // Timeline 项目状态管理，用于稳定 ID 系统 / Timeline items state management for stable ID system
    @State private var timelineItems: [TimelineItemWrapper] = []
    @State private var lastKnownItemCount: Int32 = 0
    @State private var isUpdatingItems = false

    //  Stable ID Generation Functions (1.1 Optimization)

    // 稳定 ID 生成函数 / Stable ID generation functions

    /// 为 Timeline 项目生成稳定的 ID
    /// Generate stable ID for Timeline items
    private func generateStableID(for index: Int, itemKey: String?) -> String {
        if let itemKey, !itemKey.isEmpty {
            // 使用实际的 itemKey 作为稳定 ID / Use actual itemKey as stable ID
            "timeline_item_\(itemKey)"
        } else {
            // 为占位符生成临时但稳定的 ID / Generate temporary but stable ID for placeholders
            "timeline_placeholder_\(index)"
        }
    }

    /// 更新 Timeline 项目列表，确保稳定 ID 系统正常工作 (1.1 Optimization)
    /// Update Timeline items list to ensure stable ID system works properly
    private func updateTimelineItemsIfNeeded(success: PagingStateSuccess<UiTimeline>) {
        print("🔍 [TimelineViewSwiftUI_1.1] updateTimelineItemsIfNeeded called")
        print("🔍 [TimelineViewSwiftUI_1.1] isUpdatingItems: \(isUpdatingItems)")

        guard !isUpdatingItems else {
            print("🔍 [TimelineViewSwiftUI_1.1] Already updating items, returning")
            return
        }

        let currentItemCount = success.itemCount
        print("🔍 [TimelineViewSwiftUI_1.1] currentItemCount: \(currentItemCount), lastKnownItemCount: \(lastKnownItemCount)")

        // 只有在项目数量变化时才更新 / Only update when item count changes
        guard currentItemCount != lastKnownItemCount else {
            print("🔍 [TimelineViewSwiftUI_1.1] Item count unchanged, returning")
            return
        }

        print("🔍 [TimelineViewSwiftUI_1.1] Starting update process")
        isUpdatingItems = true

        // 准备新的项目列表 / Prepare new items list
        var newItems: [TimelineItemWrapper] = []
        let newItemCount = Int(currentItemCount)
        print("🔍 [TimelineViewSwiftUI_1.1] newItemCount: \(newItemCount)")

        // 如果是首次加载或完全重新加载，创建所有项目 / If first load or complete reload, create all items
        if lastKnownItemCount == 0 || timelineItems.isEmpty {
            print("🔍 [TimelineViewSwiftUI_1.1] First load detected, creating all items")
            for index in 0 ..< newItemCount {
                let status = success.peek(index: Int32(index))
                let itemKey = status?.itemKey ?? ""
                let stableID = generateStableID(for: index, itemKey: itemKey)

                print("🔍 [TimelineViewSwiftUI_1.1] Creating item \(index): itemKey=\(itemKey), stableID=\(stableID)")

                let wrapper = TimelineItemWrapper(
                    index: index,
                    stableID: stableID,
                    itemKey: itemKey,
                    lastUpdateTime: Date()
                )

                newItems.append(wrapper)
            }
            print("🔍 [TimelineViewSwiftUI_1.1] Created \(newItems.count) new items")
        } else {
            print("🔍 [TimelineViewSwiftUI_1.1] Incremental update detected")
            // 增量更新策略：复用现有项目，只添加新项目 / Incremental update strategy: reuse existing items, only add new ones
            let oldItemCount = Int(lastKnownItemCount)

            // 复用现有的项目 / Reuse existing items
            for index in 0 ..< min(oldItemCount, newItemCount) {
                if index < timelineItems.count {
                    // 更新现有项目的索引，但保持稳定 ID / Update existing item index but keep stable ID
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

            // 添加新项目 / Add new items
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

        // 在主线程上更新状态，使用 withAnimation(.none) 避免不必要的动画
        // Update state on main thread, use withAnimation(.none) to avoid unnecessary animations
        DispatchQueue.main.async {
            withAnimation(.none) {
                timelineItems = newItems
                lastKnownItemCount = currentItemCount
                print("🔍 [TimelineViewSwiftUI_1.1] Update completed. timelineItems.count: \(timelineItems.count)")
            }
        }

        isUpdatingItems = false
        print("🔍 [TimelineViewSwiftUI_1.1] updateTimelineItemsIfNeeded finished")
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
                                // 更新 Timeline 项目列表以确保稳定 ID 系统正常工作 (1.1 Optimization)
                                // Update Timeline items list to ensure stable ID system works properly
                                let _ = {
                                    print("🔍 [TimelineViewSwiftUI_1.1] ObservePresenter success state detected")
                                    updateTimelineItemsIfNeeded(success: success)
                                }()

                                // 使用稳定 ID 系统的 ForEach，避免不必要的视图重建 (1.1 Optimization)
                                // Use stable ID system ForEach to avoid unnecessary view rebuilds
                                let _ = print("📍 [TimelineViewSwiftUI_1.1] Rendering \(timelineItems.count) items with Stable ID")

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
            .onChange(of: scrollToTopTrigger) { oldValue, newValue in
                print("[TimelineView_1.1] ScrollToTop trigger changed for tab: \(tab.key), isCurrentTab: \(isCurrentTab), oldValue: \(oldValue), newValue: \(newValue)")

                guard isCurrentTab else {
                    print("[TimelineView_1.1] Ignoring scroll trigger for inactive tab: \(tab.key)")
                    return
                }

                print("[TimelineView_1.1] Starting scroll to top for tab: \(tab.key), anchor ID: \(ScrollToTopView.Constants.scrollToTop)")

                // 改进的滚动逻辑：使用多重保障机制
                // 方法1：直接滚动到锚点
                withAnimation(.easeInOut(duration: 0.5)) {
                    proxy.scrollTo(ScrollToTopView.Constants.scrollToTop, anchor: .top)
                }

                // 方法2：备用方案 - 如果锚点不可用，滚动到第一个状态项
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    // 检查是否成功滚动到顶部，如果没有，使用备用方案
                    if let firstStatusID = scrollPositionID {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            proxy.scrollTo("StatusItemView_\(firstStatusID)", anchor: .top)
                        }
                        print("[TimelineView_1.1] Used fallback scroll to first status: \(firstStatusID)")
                    }
                }

                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                    print("[TimelineView_1.1] Scroll animation completed for tab: \(tab.key)")
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
        guard isCurrentTab else {
            return
        }

        handleFloatingButtonVisibility(offset)
    }

    private func handleFloatingButtonVisibility(_: CGFloat) {
        if !showFloatingButton {
            showFloatingButton = true
        }
    }

    //   Rendering Methods (1.1 Optimization)

    /// 渲染 Timeline 项目（稳定 ID 版本）
    /// Render Timeline item (stable ID version)
    @ViewBuilder
    private func renderTimelineItem(item: TimelineItemWrapper, success: PagingStateSuccess<UiTimeline>) -> some View {
        let index = item.index

        if let status = success.peek(index: Int32(index)) {
            let statusID = status.itemKey
            StatusItemView(data: status, detailKey: nil)
                .padding(.vertical, 8)
                .padding(.horizontal, 16)
                .id("StatusItemView_\(item.stableID)") // 使用稳定 ID / Use stable ID
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

        if index < timelineItems.count - 1 { // 使用 timelineItems.count / Use timelineItems.count
            Divider()
                .padding(.horizontal, 16)
        }
    }
}
