import SwiftUI

#if canImport(AppKit) && !canImport(UIKit)
import AppKit
#endif

// macOS has no Dynamic Type, so `.dynamicTypeSize` does not resize text styles there.
// Fonts that should follow the app's font size setting go through `fontScale` instead.
// On iOS the scale stays 1 and text styles keep scaling with Dynamic Type.

public extension EnvironmentValues {
    @Entry var fontScale: CGFloat = 1
}

public enum FlareFontScale {
    public static let fontSizeDiffRange: ClosedRange<Float> = -2...4

    public static func factor(fontSizeDiff: Float) -> CGFloat {
        1 + 0.1 * CGFloat(fontSizeDiff)
    }
}

public extension Font {
    static func flare(
        _ style: Font.TextStyle,
        scale: CGFloat,
        weight: Font.Weight? = nil,
        design: Font.Design? = nil
    ) -> Font {
        #if canImport(AppKit) && !canImport(UIKit)
        .system(
            size: style.macPointSize * scale,
            weight: weight ?? (style == .headline ? .bold : nil),
            design: design
        )
        #else
        .system(style, design: design, weight: weight)
        #endif
    }

    static func flare(size: CGFloat, scale: CGFloat, weight: Font.Weight? = nil, design: Font.Design? = nil) -> Font {
        .system(size: size * scale, weight: weight, design: design)
    }
}

public extension View {
    func scaledFont(
        _ style: Font.TextStyle,
        weight: Font.Weight? = nil,
        design: Font.Design? = nil
    ) -> some View {
        modifier(ScaledFontModifier(style: style, weight: weight, design: design))
    }
}

private struct ScaledFontModifier: ViewModifier {
    @Environment(\.fontScale) private var fontScale
    let style: Font.TextStyle
    let weight: Font.Weight?
    let design: Font.Design?

    func body(content: Content) -> some View {
        content.font(.flare(style, scale: fontScale, weight: weight, design: design))
    }
}

#if canImport(AppKit) && !canImport(UIKit)
private extension Font.TextStyle {
    var macPointSize: CGFloat {
        NSFont.preferredFont(forTextStyle: nsTextStyle).pointSize
    }

    var nsTextStyle: NSFont.TextStyle {
        switch self {
        case .largeTitle: .largeTitle
        case .title: .title1
        case .title2: .title2
        case .title3: .title3
        case .headline: .headline
        case .subheadline: .subheadline
        case .callout: .callout
        case .footnote: .footnote
        case .caption: .caption1
        case .caption2: .caption2
        default: .body
        }
    }
}
#endif
