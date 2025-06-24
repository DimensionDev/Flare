import os
import shared
import SwiftUI

//  - Timeline Item Wrapper for Stable ID System

/// 包装 Timeline 项目的数据结构，提供稳定的 ID 系统以优化 SwiftUI 渲染性能
/// Wrapper data structure for Timeline items, providing stable ID system to optimize SwiftUI rendering performance
struct TimelineItemWrapper: Identifiable, Equatable {
    let index: Int
    let stableID: String
    let itemKey: String
    let lastUpdateTime: Date

    // Identifiable 协议要求 / Required by Identifiable protocol
    var id: String { stableID }

    // Equatable 协议实现，用于 SwiftUI 差分更新 / Equatable implementation for SwiftUI diffing
    static func == (lhs: TimelineItemWrapper, rhs: TimelineItemWrapper) -> Bool {
        lhs.stableID == rhs.stableID &&
            lhs.index == rhs.index &&
            lhs.lastUpdateTime == rhs.lastUpdateTime
    }
}

//  - Dynamic Timeline View with Version Switching

/// 动态Timeline视图，支持版本切换
/// Dynamic Timeline view with version switching support
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
                TimelineViewSwiftUI1(
                    tab: tab,
                    store: store,
                    scrollPositionID: $scrollPositionID,
                    scrollToTopTrigger: $scrollToTopTrigger,
                    isCurrentTab: isCurrentTab,
                    showFloatingButton: $showFloatingButton
                )
            case .v1_1_1_2:
                TimelineViewSwiftUI1And2(
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
            // 版本切换时的额外处理逻辑可以在这里添加
            // Additional handling logic for version switching can be added here
        }
    }
}

//  - Base Version Implementation

/// Timeline基准版本实现
/// Timeline base version implementation
struct TimelineViewSwiftUIBase: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @State private var presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool
    @Binding var showFloatingButton: Bool

    var body: some View {
        // 使用Base版本的实现（从TimelineViewSwiftUI_base.swift复制）
        // Use Base version implementation (copied from TimelineViewSwiftUI_base.swift)
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
                                ForEach(0 ..< success.itemCount, id: \.self) { index in

                                    if let status = success.peek(index: index) {
                                        let statusID = status.itemKey
                                        StatusItemView(data: status, detailKey: nil)
                                            .padding(.vertical, 8)
                                            .padding(.horizontal, 16)
                                            .id("StatusItemView_\(statusID)")
                                            .onAppear {
                                                if index > success.itemCount - 4 {
                                                    success.get(index: index)
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
                                                success.get(index: index)
                                            }
                                            .padding(.vertical, 8)
                                            .padding(.horizontal, 16)
                                    }

                                    if index < success.itemCount - 1 {
                                        Divider()
                                            .padding(.horizontal, 16)
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
            .onChange(of: scrollToTopTrigger) { _, _ in
                print("[TimelineView_Base] ScrollToTop trigger changed for tab: \(tab.key)")
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
}

//  - 1.1 Version Implementation

/// Timeline 1.1版本实现（仅稳定ID优化）
/// Timeline 1.1 version implementation (Stable ID optimization only)
struct TimelineViewSwiftUI1: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @State private var presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool
    @Binding var showFloatingButton: Bool

    //  - Timeline Items State for Stable ID System (1.1 Optimization)

    @State private var timelineItems: [TimelineItemWrapper] = []
    @State private var lastKnownItemCount: Int32 = 0
    @State private var isUpdatingItems = false

    //  - Stable ID Generation Functions (1.1 Optimization)

    /// 为 Timeline 项目生成稳定的 ID
    /// Generate stable ID for Timeline items
    private func generateStableID(for index: Int, itemKey: String?) -> String {
        if let itemKey, !itemKey.isEmpty {
            "timeline_item_\(itemKey)"
        } else {
            "timeline_placeholder_\(index)"
        }
    }

    /// 更新 Timeline 项目列表，确保稳定 ID 系统正常工作 (1.1 Optimization)
    /// Update Timeline items list to ensure stable ID system works properly
    private func updateTimelineItemsIfNeeded(success: PagingStateSuccess<UiTimeline>) {
        print("🔍 [TimelineViewSwiftUI_1.1] updateTimelineItemsIfNeeded called")

        guard !isUpdatingItems else {
            print("🔍 [TimelineViewSwiftUI_1.1] Already updating items, returning")
            return
        }

        let currentItemCount = success.itemCount
        guard currentItemCount != lastKnownItemCount else {
            print("🔍 [TimelineViewSwiftUI_1.1] Item count unchanged, returning")
            return
        }

        print("🔍 [TimelineViewSwiftUI_1.1] Starting update process")
        isUpdatingItems = true

        var newItems: [TimelineItemWrapper] = []
        let newItemCount = Int(currentItemCount)

        if lastKnownItemCount == 0 || timelineItems.isEmpty {
            print("🔍 [TimelineViewSwiftUI_1.1] First load detected, creating all items")
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
            print("🔍 [TimelineViewSwiftUI_1.1] Incremental update detected")
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
                                let _ = {
                                    print("🔍 [TimelineViewSwiftUI_1.1] ObservePresenter success state detected")
                                    updateTimelineItemsIfNeeded(success: success)
                                }()

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
            .onChange(of: scrollToTopTrigger) { _, _ in
                print("[TimelineView_1.1] ScrollToTop trigger changed for tab: \(tab.key)")
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

//  - 1.1+1.2 Version Implementation

/// Timeline 1.1+1.2版本实现（稳定ID + 状态观察优化）
/// Timeline 1.1+1.2 version implementation (Stable ID + State observation optimization)
struct TimelineViewSwiftUI1And2: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @State private var presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool
    @Binding var showFloatingButton: Bool

    //  - Timeline Items State for Stable ID System (1.1 Optimization)

    @State private var timelineItems: [TimelineItemWrapper] = []
    @State private var lastKnownItemCount: Int32 = 0
    @State private var isUpdatingItems = false

    //  - State Observation Optimization (1.2 Optimization)

    @State private var totalItemCount: Int = 0

    //  - Stable ID Generation Functions

    private func generateStableID(for index: Int, itemKey: String?) -> String {
        if let itemKey, !itemKey.isEmpty {
            "timeline_item_\(itemKey)"
        } else {
            "timeline_placeholder_\(index)"
        }
    }

    /// 更新 Timeline 项目列表，确保稳定 ID 系统正常工作 (1.1+1.2 Optimization)
    private func updateTimelineItemsIfNeeded(success: PagingStateSuccess<UiTimeline>) {
        print("🔍 [TimelineViewSwiftUI_1.1+1.2] updateTimelineItemsIfNeeded called")

        guard !isUpdatingItems else {
            print("🔍 [TimelineViewSwiftUI_1.1+1.2] Already updating items, returning")
            return
        }

        let currentItemCount = success.itemCount
        guard currentItemCount != lastKnownItemCount else {
            print("🔍 [TimelineViewSwiftUI_1.1+1.2] Item count unchanged, returning")
            return
        }

        print("🔍 [TimelineViewSwiftUI_1.1+1.2] Starting update process")
        isUpdatingItems = true

        var newItems: [TimelineItemWrapper] = []
        let newItemCount = Int(currentItemCount)

        if lastKnownItemCount == 0 || timelineItems.isEmpty {
            print("🔍 [TimelineViewSwiftUI_1.1+1.2] First load detected, creating all items")
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
            print("🔍 [TimelineViewSwiftUI_1.1+1.2] Incremental update detected")
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

        // 1.2 优化：更精细的状态观察和更新
        // 1.2 Optimization: More fine-grained state observation and updates
        DispatchQueue.main.async {
            withAnimation(.none) {
                timelineItems = newItems
                lastKnownItemCount = currentItemCount
                totalItemCount = Int(currentItemCount) // 1.2: 状态观察优化
                print("🔍 [TimelineViewSwiftUI_1.1+1.2] Update completed. timelineItems.count: \(timelineItems.count)")
            }
        }

        isUpdatingItems = false
        print("🔍 [TimelineViewSwiftUI_1.1+1.2] updateTimelineItemsIfNeeded finished")
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
                                    print("🔍 [TimelineViewSwiftUI_1.1+1.2] ObservePresenter success state detected")
                                    updateTimelineItemsIfNeeded(success: success)
                                }()

                                let _ = print("📍 [TimelineViewSwiftUI_1.1+1.2] Rendering \(timelineItems.count) items with Stable ID + State Observation")

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
                print("[TimelineView_1.1+1.2] ScrollToTop trigger changed for tab: \(tab.key)")
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
