import shared
import SwiftUI
import WaterfallGrid

struct WaterfallView: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    let isCurrentTab: Bool
    let displayType: TimelineDisplayType

    @Environment(FlareTheme.self) private var theme

    @State private var viewModel = TimelineViewModel()
    @State private var scrolledID: String?
    @State private var isInitialized: Bool = false
    @State private var refreshDebounceTimer: Timer?

    init(tab: FLTabItem, store: AppBarTabSettingStore, isCurrentTab: Bool, displayType: TimelineDisplayType) {
        self.tab = tab
        self.store = store
        self.isCurrentTab = isCurrentTab
        self.displayType = displayType
        FlareLog.debug("🔍 [WaterfallView] 视图初始化 for tab: '\(tab.key)', received isCurrentTab: \(isCurrentTab)")
    }

    var body: some View {
        Group {
            switch viewModel.timelineState {
            case .loading:
                TimelineLoadingView()

            case let .loaded(items, hasMore):
                WaterfallItemsView(
                    items: items,
                    displayType: displayType,
                    hasMore: hasMore,
                    onError: viewModel.handleError,
                    scrolledID: $scrolledID,
                    isCurrentTab: isCurrentTab,
                    viewModel: viewModel
                )

            case let .error(error):
                TimelineErrorView(error: error) {
                    Task { @MainActor in
                        await viewModel.handleRefresh()
                    }
                }

            case .empty:
                TimelineEmptyView()
            }
        }
        .task(id: tab.key) {
            FlareLog.debug("📱 [WaterfallView] .task(id: \(tab.key)) triggered - isCurrentTab: \(isCurrentTab) ")

            if !isInitialized {
                isInitialized = true
                FlareLog.debug("🚀 [WaterfallView] First time initialization for tab: \(tab.key)")
                await viewModel.setupDataSource(for: tab, using: store)
                FlareLog.debug("✅ [WaterfallView] setupDataSource completed for tab: \(tab.key)")
            } else {
                FlareLog.debug("⏭️ [WaterfallView] Tab reappeared, skipping setupDataSource for tab: \(tab.key)")
            }
        }
        .onAppear {
            // FlareLog.debug("👁️ [WaterfallView] onAppear - tab: \(tab.key), isCurrentTab: \(isCurrentTab) ")

//            if isCurrentTab {
            //  FlareLog.debug("✅ [WaterfallView] Current tab, calling resume - tab: \(tab.key)")
            viewModel.resume()
//            } else {
//                FlareLog.debug("⏸️ [WaterfallView] Not current tab, skipping resume - tab: \(tab.key)")
//            }
        }
        .onDisappear {
            FlareLog.debug("👋 [WaterfallView] onDisappear - tab: \(tab.key), isCurrentTab: \(isCurrentTab)")

            // 无论isCurrentTab值如何，都尝试暂停，让ViewModel内部判断
            FlareLog.debug("⏸️ [WaterfallView] Calling pause for tab: \(tab.key)")
            viewModel.pause()
        }
        .onReceive(NotificationCenter.default.publisher(for: .timelineItemUpdated)) { _ in
            FlareLog.debug("📬 [WaterfallView] Received timelineItemUpdated notification - tab: \(tab.key), isCurrentTab: \(isCurrentTab) ")

            refreshDebounceTimer?.invalidate()
            FlareLog.debug("⏰ [WaterfallView] Setting refresh debounce timer - tab: \(tab.key), isCurrentTab: \(isCurrentTab)")

            refreshDebounceTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { _ in
                FlareLog.debug("⏱️ [WaterfallView] Debounce timer fired - tab: \(tab.key), isCurrentTab: \(isCurrentTab),  ")

                guard isCurrentTab else {
                    FlareLog.debug("⏸️ [WaterfallView] Skipping refresh - not current tab: \(tab.key), isCurrentTab: \(isCurrentTab)")
                    return
                }

                FlareLog.debug("🔄 [WaterfallView] Starting handleRefresh - tab: \(tab.key), isCurrentTab: \(isCurrentTab)")
                Task {
                    await viewModel.handleRefresh()
                    await MainActor.run {
                        FlareLog.debug("✅ [WaterfallView] handleRefresh completed - tab: \(tab.key), timestamp: \(Date().timeIntervalSince1970)")
                    }
                }
            }
        }
    }
}
