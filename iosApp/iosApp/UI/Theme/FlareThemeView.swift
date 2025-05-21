import SwiftUI

@MainActor
public struct FlareThemeView<Content: View>: View {
    @Environment(FlareTheme.self) private var theme
    // @State private var theme = FlareTheme.shared
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

public extension View {
    func withFlareTheme() -> some View {
        FlareThemeView {
            self
        }
    }
}
