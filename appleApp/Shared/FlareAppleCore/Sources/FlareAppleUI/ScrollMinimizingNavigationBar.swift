import SwiftUI

public struct ScrollMinimizingNavigationBar: ViewModifier {
    public init() {}

    @ViewBuilder
    public func body(content: Content) -> some View {
        #if os(iOS)
        if #available(iOS 27.0, *) {
            content.toolbarMinimizationBehavior(
                .onScrollDown,
                for: .navigationBar
            )
        } else {
            content
        }
        #else
        content
        #endif
    }
}
