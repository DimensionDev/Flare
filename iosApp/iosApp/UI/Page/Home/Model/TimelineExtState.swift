import SwiftUI

@Observable
class TimelineExtState {
    var scrollToTopTrigger = false
    var showFloatingButton = false

    var tabBarOffset: CGFloat = 0 // TabBar偏移量，0=显示，100=隐藏
    private var lastScrollOffset: CGFloat = 0

    func updateTabBarOffset(currentOffset: CGFloat, isHomeTab: Bool) {
       
        guard isHomeTab else {
            if tabBarOffset != 0 {
                tabBarOffset = 0
            }
            return
        }

        //  滚动到顶部时显示TabBar
        if currentOffset <= 0 {
            if tabBarOffset != 0 {
                tabBarOffset = 0
                lastScrollOffset = currentOffset
            }
            return
        }

        let scrollDelta = currentOffset - lastScrollOffset

        // 向下滚动 隐藏TabBar
        if scrollDelta > 0 {
            guard tabBarOffset != 100 else {
                lastScrollOffset = currentOffset
                return
            }

            if scrollDelta > 30 {
                tabBarOffset = 100
                lastScrollOffset = currentOffset
            }
        }

        // 向上滚动 显示TabBar
        else if scrollDelta < 0 {

            guard tabBarOffset != 0 else {
                lastScrollOffset = currentOffset
                return
            }

            if scrollDelta < -10 {
                tabBarOffset = 0
                lastScrollOffset = currentOffset
            }
        }
    }
}
