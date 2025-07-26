import Foundation
import os
import shared
import SwiftUI

class AppBarPresenterService {
    private var presenterCache: [String: TimelinePresenter] = [:]

    func getOrCreatePresenter(for tab: FLTabItem) -> TimelinePresenter? {
        guard let timelineTab = tab as? FLTimelineTabItem else { return nil }

        if let cached = presenterCache[tab.key] {
            return cached
        }
        let presenter = timelineTab.createPresenter()
        presenterCache[tab.key] = presenter
        return presenter
    }

    func clearCache() {
        presenterCache.removeAll()
    }
}
