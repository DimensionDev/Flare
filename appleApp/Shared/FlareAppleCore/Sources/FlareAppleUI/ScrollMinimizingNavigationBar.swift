import SwiftUI

public struct ScrollMinimizingNavigationBar: ViewModifier {
    private let enabled: Bool

    public init(enabled: Bool = true) {
        self.enabled = enabled
    }

    @ViewBuilder
    public func body(content: Content) -> some View {
        #if os(iOS)
        if #available(iOS 27.0, *) {
            content.toolbarMinimizationBehavior(
                enabled ? .onScrollDown : .never,
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
