import SwiftUI

struct FlareThemeView<Child>: View where Child: View {
    let content: () -> Child
    var body: some View {
        // 使用Theme.shared提供的设置
        ZStack {
            if let theme = Theme.shared {
                theme.primaryBackgroundColor.ignoresSafeArea()
                content()
                    .foregroundColor(theme.labelColor)
                    .tint(theme.tintColor)
                    .preferredColorScheme(theme.colorScheme)
            } else {
                // 回退方案
                Colors.Background.swiftUIPrimary.ignoresSafeArea()
                content()
            }
        }
    }
}
