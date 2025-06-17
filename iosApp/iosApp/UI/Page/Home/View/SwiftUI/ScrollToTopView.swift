import SwiftUI

struct ScrollToTopView: View {
    enum Constants {
        static let scrollToTop = "scroll_to_top"
    }

    let tabKey: String

    var body: some View {
        // 使用 Rectangle 而不是 Color.clear，确保在 LazyVStack 中的稳定性
        Rectangle()
            .fill(Color.clear)
            .frame(width: 1, height: 1) // 使用最小的可见尺寸，确保不被 LazyVStack 回收
            .clipped() // 确保视图边界正确
            .allowsHitTesting(false) // 不干扰用户交互
            .accessibilityHidden(true)
            .id(Constants.scrollToTop)
            .onAppear {
                print("[ScrollToTopView] Anchor view appeared for tab: \(tabKey)")
            }
            .onDisappear {
                print("[ScrollToTopView] Anchor view disappeared for tab: \(tabKey)")
            }
    }
}
