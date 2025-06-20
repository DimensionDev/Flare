import os
import shared
import SwiftUI

//   Timeline 1.1+1.2 Version (Stable ID + State Observation)

struct TimelineViewSwiftUI_1_1_1_2_Standalone: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @State private var presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool

    @Binding var showFloatingButton: Bool

    //  Timeline Items State for Stable ID System

    // Timeline 项目状态管理，用于稳定 ID 系统 / Timeline items state management for stable ID system
    @State private var timelineItems: [TimelineItemWrapper] = []
    @State private var lastKnownItemCount: Int32 = 0
    @State private var isUpdatingItems = false

    //   Visible Range Management

    // 可见范围管理，用于性能优化 / Visible range management for performance optimization
    @State private var visibleRangeManager = VisibleRangeManager()
    @State private var totalItemCount: Int = 0

    //  Performance Test Configuration

    // 性能测试配置 / Performance test configuration
    private let testConfig = PerformanceTestConfig.shared

    //  Stable ID Generation Functions

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

    /// 更新 Timeline 项目列表，确保稳定 ID 系统正常工作
    /// Update Timeline items list to ensure stable ID system works properly
    private func updateTimelineItemsIfNeeded(success: PagingStateSuccess<UiTimeline>) {
        print("🔍 [TimelineViewSwiftUI] updateTimelineItemsIfNeeded called")
        print("🔍 [TimelineViewSwiftUI] isUpdatingItems: \(isUpdatingItems)")

        guard !isUpdatingItems else {
            print("🔍 [TimelineViewSwiftUI] Already updating items, returning")
            return
        }

        let currentItemCount = success.itemCount
        print("🔍 [TimelineViewSwiftUI] currentItemCount: \(currentItemCount), lastKnownItemCount: \(lastKnownItemCount)")

        // 只有在项目数量变化时才更新 / Only update when item count changes
        guard currentItemCount != lastKnownItemCount else {
            print("🔍 [TimelineViewSwiftUI] Item count unchanged, returning")
            return
        }

        print("🔍 [TimelineViewSwiftUI] Starting update process")
        isUpdatingItems = true

        // 准备新的项目列表 / Prepare new items list
        var newItems: [TimelineItemWrapper] = []
        let newItemCount = Int(currentItemCount)
        print("🔍 [TimelineViewSwiftUI] newItemCount: \(newItemCount)")

        // 如果是首次加载或完全重新加载，创建所有项目 / If first load or complete reload, create all items
        if lastKnownItemCount == 0 || timelineItems.isEmpty {
            print("🔍 [TimelineViewSwiftUI] First load detected, creating all items")
            for index in 0 ..< newItemCount {
                let status = success.peek(index: Int32(index))
                let itemKey = status?.itemKey ?? ""
                let stableID = generateStableID(for: index, itemKey: itemKey)

                print("🔍 [TimelineViewSwiftUI] Creating item \(index): itemKey=\(itemKey), stableID=\(stableID)")

                let wrapper = TimelineItemWrapper(
                    index: index,
                    stableID: stableID,
                    itemKey: itemKey,
                    lastUpdateTime: Date()
                )

                newItems.append(wrapper)
            }
            print("🔍 [TimelineViewSwiftUI] Created \(newItems.count) new items")
        } else {
            print("🔍 [TimelineViewSwiftUI] Incremental update detected")
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
                print("🔍 [TimelineViewSwiftUI] Update completed. timelineItems.count: \(timelineItems.count)")
            }
        }

        isUpdatingItems = false
        print("🔍 [TimelineViewSwiftUI] updateTimelineItemsIfNeeded finished")
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
                                // 更新 Timeline 项目列表以确保稳定 ID 系统正常工作
                                // Update Timeline items list to ensure stable ID system works properly
                                let _ = {
                                    print("🔍 [TimelineViewSwiftUI] ObservePresenter success state detected")
                                    updateTimelineItemsIfNeeded(success: success)
                                    // 更新总项目数量 / Update total item count
                                    totalItemCount = Int(success.itemCount)
                                }()

                                // 根据测试配置选择渲染方式 / Choose rendering method based on test configuration
                                let _ = print("🔍 [TimelineViewSwiftUI] Rendering ForEach with \(timelineItems.count) items")

                                if testConfig.enableStableIDSystem {
                                    // 使用稳定 ID 系统的 ForEach，避免不必要的视图重建
                                    // Use stable ID system ForEach to avoid unnecessary view rebuilds
                                    let visibleItems = testConfig.enableVisibleRangeDetection ? getVisibleItems(from: timelineItems) : timelineItems
                                    let _ = print("📍 [TimelineViewSwiftUI] Rendering \(visibleItems.count) visible items out of \(timelineItems.count) total (Stable ID)")

                                    ForEach(visibleItems, id: \.stableID) { item in
                                        renderTimelineItem(item: item, success: success)
                                    }
                                } else {
                                    // 使用原始的基于索引的 ForEach（基准测试）
                                    // Use original index-based ForEach (baseline test)
                                    let itemCount = testConfig.enableVisibleRangeDetection ? min(30, Int(success.itemCount)) : Int(success.itemCount)
                                    let _ = print("📍 [TimelineViewSwiftUI] Rendering \(itemCount) items out of \(success.itemCount) total (Index-based)")

                                    ForEach(0 ..< itemCount, id: \.self) { index in
                                        renderTimelineItemByIndex(index: index, success: success)
                                    }
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
                print("[TimelineView] ScrollToTop trigger changed for tab: \(tab.key), isCurrentTab: \(isCurrentTab), oldValue: \(oldValue), newValue: \(newValue)")

                guard isCurrentTab else {
                    print("[TimelineView] Ignoring scroll trigger for inactive tab: \(tab.key)")
                    return
                }

                print("[TimelineView] Starting scroll to top for tab: \(tab.key), anchor ID: \(ScrollToTopView.Constants.scrollToTop)")

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
                        print("[TimelineView] Used fallback scroll to first status: \(firstStatusID)")
                    }
                }

                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                    print("[TimelineView] Scroll animation completed for tab: \(tab.key)")
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
        .onPreferenceChange(VisibleRangePreferenceKey.self) { visibleRange in
            handleVisibleRangeChange(visibleRange)
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

    //  Visible Range Handling

    /// 处理可见范围变化
    /// Handle visible range changes
    private func handleVisibleRangeChange(_ visibleRange: VisibleRange) {
        guard isCurrentTab, testConfig.enableVisibleRangeDetection else {
            return
        }

        // 更新可见范围管理器
        // Update visible range manager
        visibleRangeManager.updateRange(visibleRange)

        // 调试日志 / Debug logging
        if PerformanceConfig.isVerboseLoggingEnabled {
            let stats = visibleRangeManager.rangeStats
            print("📍 [TimelineViewSwiftUI] Visible range updated for tab \(tab.key): \(visibleRange.startIndex)-\(visibleRange.endIndex), visible: \(stats.visible), buffered: \(stats.buffered)")
        }
    }

    /// 获取可见范围内的项目
    /// Get items within visible range
    private func getVisibleItems(from items: [TimelineItemWrapper]) -> [TimelineItemWrapper] {
        let currentRange = visibleRangeManager.currentRange

        // 如果没有有效的可见范围，返回前几个项目作为默认
        // If no valid visible range, return first few items as default
        guard currentRange.startIndex >= 0, currentRange.endIndex >= currentRange.startIndex else {
            let defaultCount = min(20, items.count) // 默认显示前 20 个项目 / Default to first 20 items
            return Array(items.prefix(defaultCount))
        }

        // 过滤可见范围内的项目（包含缓冲区）
        // Filter items within visible range (including buffer)
        let visibleItems = items.filter { item in
            visibleRangeManager.shouldRender(item.index)
        }

        // 1.2优化：智能控制placeholder显示，允许少量placeholder用于数据加载触发 / 1.2 Optimization: smart placeholder control, allow few placeholders for data loading trigger
        if let presenter,
           let timelineState = presenter.models.value as? TimelineState,
           case let .success(success) = onEnum(of: timelineState.listState)
        {
            // 计算连续placeholder的数量，限制显示过多空项目 / Count consecutive placeholders to limit excessive empty items
            var result: [TimelineItemWrapper] = []
            var consecutivePlaceholders = 0
            let maxConsecutivePlaceholders = 5 // 最多允许5个连续placeholder / Max 5 consecutive placeholders

            for item in visibleItems {
                let hasData = success.peek(index: Int32(item.index)) != nil

                if hasData {
                    // 有数据，重置计数器并添加项目 / Has data, reset counter and add item
                    consecutivePlaceholders = 0
                    result.append(item)
                } else {
                    // 无数据，检查是否超过限制 / No data, check if exceeds limit
                    if consecutivePlaceholders < maxConsecutivePlaceholders {
                        consecutivePlaceholders += 1
                        result.append(item)
                    }
                    // 超过限制则跳过，避免显示过多placeholder / Skip if exceeds limit to avoid too many placeholders
                }
            }

            return result
        }

        // 如果过滤后的项目太少，返回更多项目以确保流畅滚动
        // If filtered items are too few, return more items to ensure smooth scrolling
        if visibleItems.count < 10 {
            let fallbackCount = min(30, items.count)
            return Array(items.prefix(fallbackCount))
        }

        return visibleItems
    }

    //  Rendering Methods

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
                .visibleRangeDetector(index: index, totalItems: totalItemCount) // 添加可见范围检测 / Add visible range detection
                .onAppear {
                    // 优化：只在接近底部时触发预加载，避免过度加载 / Optimization: only trigger preload when near bottom to avoid over-loading
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
            // 1.2优化：如果数据不可用，显示placeholder但限制数量，避免无限滚动 / 1.2 Optimization: if data unavailable, show placeholder but limit count to avoid infinite scroll
            StatusPlaceHolder()
                .onAppear {
                    // 只在合理范围内触发数据加载 / Only trigger data loading within reasonable range
                    if index < Int(success.itemCount) {
                        success.get(index: Int32(index))
                    }
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 16)
                .visibleRangeDetector(index: index, totalItems: totalItemCount) // 添加可见范围检测 / Add visible range detection
        }

        if index < timelineItems.count - 1 { // 使用 timelineItems.count / Use timelineItems.count
            Divider()
                .padding(.horizontal, 16)
        }
    }

    /// 渲染 Timeline 项目（基于索引版本，用于基准测试）
    /// Render Timeline item (index-based version, for baseline testing)
    @ViewBuilder
    private func renderTimelineItemByIndex(index: Int, success: PagingStateSuccess<UiTimeline>) -> some View {
        if let status = success.peek(index: Int32(index)) {
            let statusID = status.itemKey
            StatusItemView(data: status, detailKey: nil)
                .padding(.vertical, 8)
                .padding(.horizontal, 16)
                .id("StatusItemView_\(index)") // 使用索引作为 ID / Use index as ID
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

        if index < Int(success.itemCount) - 1 {
            Divider()
                .padding(.horizontal, 16)
        }
    }
}
