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

        //    if let presenter = getOrCreatePresenter(for: tab) {
        //     // 先设置为 nil，触发 UI 更新
        //     currentPresenter = nil
        //     // 使用 MainActor 确保在主线程更新 UI
        //     Task { @MainActor in
        //         // 短暂延迟，确保 nil 状态被处理
        //         try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 秒
        //         currentPresenter = presenter
        //     }
        // }
    }

    func refresh() async throws {
        guard let presenter = currentPresenter else { return }

        await MainActor.run {
            isRefreshing = true
        }

        defer {
            Task { @MainActor in
                isRefreshing = false
            }
        }

        let state = presenter.models.value
        try await state.refresh()
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
    }

    func handleMemoryWarning() {
        clearCache()
    }

    func handleBackground() {
        clearCache()
    }
}
