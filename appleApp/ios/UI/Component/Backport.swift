import SwiftUI
import SwiftUIBackports

public extension Backport where Content: View {
    @ViewBuilder
    func labelIconToTitleSpacing(_ spacing: CGFloat) -> some View {
        #if os(iOS)
        if #available(iOS 26.0, *) {
            content.labelIconToTitleSpacing(spacing)
        } else {
            content.labelStyle(BackportLabelStyle(spacing: spacing))
        }
        #else
        content.labelStyle(BackportLabelStyle(spacing: spacing))
        #endif
    }
    
    @ViewBuilder
    func navigationSubtitle(_ subtitle: Text) -> some View {
        #if os(iOS)
        if #available(iOS 26.0, *) {
            content.navigationSubtitle(subtitle)
        } else {
            content
        }
        #else
        content
        #endif
    }
    
    @ViewBuilder
    func navigationSubtitle<S>(_ subtitle: S) -> some View where S : StringProtocol {
        #if os(iOS)
        if #available(iOS 26.0, *) {
            content.navigationSubtitle(subtitle)
        } else {
            content
        }
        #else
        content
        #endif
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

public extension Backport where Content: View {
    @ViewBuilder
    func textRenderer<T>(_ renderer: T) -> some View where T : TextRenderer {
        if #available(iOS 18.0, macOS 15.0, *) {
            content.textRenderer(renderer)
        } else {
            content
        }
    }
}
