
import Combine
import Kingfisher
import shared
import SwiftUI

// struct TimelineViewSwiftUIV2: View {
//    let tab: FLTabItem
//    @ObservedObject var store: AppBarTabSettingStore
//    let isCurrentTab: Bool
//    @EnvironmentObject private var timelineState: TimelineExtState
//
//    private var scrollPositionBinding: Binding<String?> {
//        Binding<String?>(
//            get: {
//                return timelineState.getScrollPosition(for: tab.key)
//            },
//            set: { newValue in
//                timelineState.setScrollPosition(for: tab.key, itemId: newValue)
//            }
//        )
//    }
//
//    @State private var presenter: TimelinePresenter?
//
//    @State private var stateConverter = PagingStateConverter()
//
//    @State private var localTimelineState: FlareTimelineState = .loading
//
//    @State private var showErrorAlert = false
//
//    @State private var currentError: FlareError?
//
//    @State private var cancellables = Set<AnyCancellable>()
//
//    @State private var refreshDebounceTimer: Timer?
//
//    var body: some View {
//        ScrollViewReader { proxy in
//            ScrollView {
//                LazyVStack(spacing: 0) {
//                    ScrollToTopView(tabKey: tab.key)
//                        .id("top")
//                        .background(
//                            GeometryReader { geometry in
//                                Color.clear
//                                    .preference(key: ScrollOffsetPreferenceKey.self,
//                                                value: geometry.frame(in: .global).minY)
//                            }
//                        )
//
//                    // 使用简化的状态管理
//                    TimelineContentViewV2(
//                        state: localTimelineState,
//                        presenter: presenter,
//                        scrollPositionID: scrollPositionBinding,
//                        onError: { error in
//                            currentError = error
//                            showErrorAlert = true
//                        },
//                        viewModel: nil
//                    )
//                }
//            }
//            .onChange(of: timelineState.scrollToTopTrigger) { _, _ in
//                let _ = FlareLog.debug("TimelineView_v2 ScrollToTop trigger changed for tab: \(tab.key)")
//                guard isCurrentTab else { return }
//
//                withAnimation(.easeInOut(duration: 0.5)) {
//                    proxy.scrollTo(ScrollToTopView.Constants.scrollToTop, anchor: .top)
//                }
//            }
//        }
//        .scrollPosition(id: scrollPositionBinding)
//        .refreshable {
//            await handleRefresh()
//        }
//        .task {
//            await setupDataSource()
//        }
//        .onReceive(NotificationCenter.default.publisher(for: .timelineItemUpdated)) { _ in
//            FlareLog.debug("TimelineView_v2 Received item update notification for tab: \(tab.key)")
//
//            // 🔥 防抖机制：取消之前的定时器，设置新的定时器
//            refreshDebounceTimer?.invalidate()
//            refreshDebounceTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { _ in
//                // 只有当前tab才刷新
//                guard isCurrentTab else { return }
//
//                Task {
//                    await handleRefresh()
//                }
//            }
//        }
//        .coordinateSpace(name: "scroll")
//        .onPreferenceChange(ScrollOffsetPreferenceKey.self) { offset in
//            handleScrollOffsetChange(offset)
//        }
//        .onDisappear {
//            cancellables.removeAll()
//        }
//    }
//
//    private func setupDataSource() async {
//        FlareLog.debug("TimelineViewSwiftUI_v2 Setting up direct data flow for tab: \(tab.key)")
//
//        guard let kmpPresenter = store.getOrCreatePresenter(for: tab) else {
//            FlareLog.error("TimelineViewSwiftUI_v2 Failed to get presenter for tab: \(tab.key)")
//            await MainActor.run {
//                localTimelineState = .error(.data(.parsing))
//            }
//            return
//        }
//
//        await MainActor.run {
//            presenter = kmpPresenter
//
//            Task {
//                for await state in kmpPresenter.models {
//                    await MainActor.run {
//                        guard let timelineState = state as? TimelineState else {
//                            return
//                        }
//
//                        let newState = stateConverter.convert(timelineState.listState)
//                        let oldState = self.localTimelineState
//
//                        if newState != oldState {
//                            self.localTimelineState = newState
//                            FlareLog.debug("TimelineViewSwiftUI_v2 State updated: \(newState.description)")
//                        }
//                    }
//                }
//            }
//        }
//
//        FlareLog.debug("TimelineViewSwiftUI_v2 Direct data flow setup completed for tab: \(tab.key)")
//    }
//
//    private func handleRefresh() async {
//        FlareLog.debug("TimelineViewSwiftUI_v2 Handling refresh for tab: \(tab.key)")
//
//        guard let presenter else {
//            FlareLog.warning("TimelineViewSwiftUI_v2 No presenter available for refresh")
//            return
//        }
//
//        do {
//            // 重置转换器状态
//            stateConverter.reset()
//
//            // 直接调用KMP的刷新方法
//            let timelineState = presenter.models.value
//            if let timelineState = timelineState as? TimelineState {
//                try await timelineState.refresh()
//            }
//            FlareLog.debug("TimelineViewSwiftUI_v2 Refresh completed for tab: \(tab.key)")
//        } catch {
//            FlareLog.error("TimelineViewSwiftUI_v2 Refresh failed: \(error)")
//        }
//    }
//
//    private func handleScrollOffsetChange(_: CGFloat) {
//        if !timelineState.showFloatingButton {
//            timelineState.showFloatingButton = true
//        }
//    }
// }
