import Foundation
import os
import shared
import SwiftUI

// Presenter管理服务
class AppBarPresenterService {
    private let logger = Logger(subsystem: "com.flare.app", category: "AppBarPresenterService")

    // 缓存 presenter 避免重复创建
    private var presenterCache: [String: TimelinePresenter] = [:]

    // 获取或创建 Presenter
    func getOrCreatePresenter(for tab: FLTabItem) -> TimelinePresenter? {
        guard let timelineTab = tab as? FLTimelineTabItem else { return nil }

        if let cached = presenterCache[tab.key] {
            return cached
        }
        let presenter = timelineTab.createPresenter()
        presenterCache[tab.key] = presenter
        return presenter
    }

    // 清除缓存
    func clearCache() {
        presenterCache.removeAll()
    }
}
