import SwiftUI

@MainActor
public struct FlareThemeView<Content: View>: View {
    @State private var theme = FlareTheme.shared
    private let content: Content
    
    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }
    
    public var body: some View {
        content
            .environment(theme)
            .applyTheme(theme)
    }
}

extension View {
    public func withFlareTheme() -> some View {
        FlareThemeView {
            self
        }
    }
} 