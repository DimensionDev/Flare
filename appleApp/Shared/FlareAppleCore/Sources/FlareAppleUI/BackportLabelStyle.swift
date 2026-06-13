import SwiftUI
import SwiftUIBackports

public extension Backport where Content: View {
    @ViewBuilder
    func flareLabelIconToTitleSpacing(_ spacing: CGFloat) -> some View {
        #if os(iOS)
        if #available(iOS 26.0, *) {
            content.labelIconToTitleSpacing(spacing)
        } else {
            content.labelStyle(FlareBackportLabelStyle(spacing: spacing))
        }
        #else
        content.labelStyle(FlareBackportLabelStyle(spacing: spacing))
        #endif
    }
}

private struct FlareBackportLabelStyle: LabelStyle {
    let spacing: CGFloat

    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: spacing) {
            configuration.icon
            configuration.title
        }
    }
}
