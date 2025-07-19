import SwiftUI

class TimelineExtState: ObservableObject {
    @Published var scrollToTopTrigger = false
    @Published var showFloatingButton = false
    // @Published var scrollPositions: [String: String] = [:] //timeline v3,

    // func getScrollPosition(for tabKey: String) -> String? {
    //     return scrollPositions[tabKey]
    // }

    // func setScrollPosition(for tabKey: String, itemId: String?) {
    //     if let itemId = itemId {
    //         scrollPositions[tabKey] = itemId
    //     } else {
    //         scrollPositions.removeValue(forKey: tabKey)
    //     }
    // }
}
