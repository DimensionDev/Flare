import Foundation
import shared
import SwiftUI

// 在文件顶部添加通知名称定义
extension Notification.Name {
    static let accountChanged = Notification.Name("accountChanged")
}

class TimelineStore: ObservableObject {
    @Published var currentPresenter: TimelinePresenter?
    @Published var homeTimelinePresenter: HomeTimelinePresenter
    @Published private(set) var selectedTabKey: String?
    @Published private(set) var isRefreshing: Bool = false
    private var presenterCache: [String: TimelinePresenter] = [:]

    // 保存滚动位置
    private var scrollPositions: [String: CGFloat] = [:]
    private var contentSizes: [String: CGSize] = [:]

    init(accountType: AccountType) {
        homeTimelinePresenter = HomeTimelinePresenter(accountType: accountType)

        // 监听账号切换事件
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAccountChanged),
            name: .accountChanged,
            object: nil
        )
    }

    @objc private func handleAccountChanged() {
        // 完全重置状态
        selectedTabKey = nil
        currentPresenter = nil
        presenterCache.removeAll()
        clearCache()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    func getOrCreatePresenter(for tab: FLTabItem) -> TimelinePresenter? {
        if let timelineItem = tab as? FLTimelineTabItem {
            let key = tab.key
            if let cachedPresenter = presenterCache[key] {
                return cachedPresenter
            } else {
                let presenter = timelineItem.createPresenter()
                presenterCache[key] = presenter
                return presenter
            }
        }
        return nil
    }

    func updateCurrentPresenter(for tab: FLTabItem) {
        selectedTabKey = tab.key
        if let presenter = getOrCreatePresenter(for: tab) {
            currentPresenter = nil
            DispatchQueue.main.async {
                self.currentPresenter = presenter
            }
        }

        //   if let presenter = getOrCreatePresenter(for: tab) {
        //    // 先设置为 nil，触发 UI 更新
        //    currentPresenter = nil
        //    // 使用 MainActor 确保在主线程更新 UI
        //    Task { @MainActor in
        //        // 短暂延迟，确保 nil 状态被处理
        //        try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 秒
        //        currentPresenter = presenter
        //    }
        // }
    }

    func refresh() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }

        if let timelineState = currentPresenter?.models.value as? TimelineState {
            try? await timelineState.refresh()
        }
    }

    func loadMore() async {
        if let timelineState = currentPresenter?.models.value as? TimelineState,
           case let .success(data) = onEnum(of: timelineState.listState)
        {
            let appendState = data.appendState
            if let notLoading = appendState as? Paging_commonLoadState.NotLoading,
               !notLoading.endOfPaginationReached
            {
                data.retry()
            }
        }
    }

    func reset() {
        selectedTabKey = nil
        currentPresenter = nil
        clearCache()
    }

    func clearCache() {
        // 保留当前 presenter，清理其他缓存
        let currentKey = selectedTabKey
        let current = currentPresenter
        presenterCache.removeAll()
        if let key = currentKey {
            presenterCache[key] = current
        }
        scrollPositions.removeAll()
        contentSizes.removeAll()
    }

    func handleMemoryWarning() {
        clearCache()
    }

    func handleBackground() {
        clearCache()
    }

    // 保存滚动位置
    func saveScrollPosition(_ position: CGFloat, for key: String) {
        scrollPositions[key] = position
    }

    // 获取滚动位置
    func getScrollPosition(for key: String) -> CGFloat {
        scrollPositions[key] ?? 0
    }

    // 保存内容大小
    func saveContentSize(_ size: CGSize, for key: String) {
        contentSizes[key] = size
    }

    // 获取内容大小
    func getContentSize(for key: String) -> CGSize {
        contentSizes[key] ?? .zero
    }

    // 监听账号变化
    func observeAccountChanges() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAccountChange),
            name: NSNotification.Name("AccountChanged"),
            object: nil
        )
    }

    @objc private func handleAccountChange() {
        clearCache()
    }
}
