import SwiftUI
import SwiftUIBackports

extension Backport where Content: View {
    @ViewBuilder
    func labelIconToTitleSpacing(_ spacing: CGFloat) -> some View {
        if #available(iOS 26.0, *) {
            content.labelIconToTitleSpacing(spacing)
        } else {
            content.labelStyle(BackportLabelStyle(spacing: spacing))
        }
    }
}

struct BackportLabelStyle: LabelStyle {
    let spacing: CGFloat
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: spacing) {
            configuration.icon
            configuration.title
        }
    }
}

extension Backport where Content: View {
    @ViewBuilder
    func textRenderer<T>(_ renderer: T) -> some View where T : TextRenderer {
        if #available(iOS 18.0, *) {
            content.textRenderer(renderer)
        } else {
            content
        }
    }
}
