import SwiftUI

class TimelineExtState: ObservableObject {
    @Published var scrollToTopTrigger = false
    @Published var showFloatingButton = false

    @Published var tabBarOffset: CGFloat = 0 // TabBar偏移量，0=显示，100=隐藏
    private var lastScrollOffset: CGFloat = 0

    func updateTabBarOffset(currentOffset: CGFloat, isHomeTab: Bool) {
        guard isHomeTab else {
            if tabBarOffset != 0 {
                tabBarOffset = 0
            }
            return
        }

        if currentOffset <= 0 {
            if tabBarOffset != 0 {
                tabBarOffset = 0
            }
            lastScrollOffset = currentOffset
            return
        }

        let scrollDelta = currentOffset - lastScrollOffset

        if scrollDelta > 30, tabBarOffset == 0 {
            tabBarOffset = 100
        }

        else if scrollDelta < -30, tabBarOffset != 0 {
            tabBarOffset = 0
        }

        lastScrollOffset = currentOffset
    }
}
