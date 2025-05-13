import SwiftUI

// ColorSet protocol defines all colors used in the app
public protocol ColorSet {
    var primary: Color { get }
    var secondary: Color { get }
    var accent: Color { get }
    var background: Color { get }
    var secondaryBackground: Color { get }
    var tertiaryBackground: Color { get }
    var label: Color { get }
    var secondaryLabel: Color { get }
    var tertiaryLabel: Color { get }
    var border: Color { get }
    var link: Color { get }
    var notification: Color { get }
    var success: Color { get }
    var warning: Color { get }
    var error: Color { get }
}

// System default color set that follows iOS dynamic colors
public struct SystemColorSet: ColorSet {
    public var primary: Color { Color(.label) }
    public var secondary: Color { Color(.secondaryLabel) }
    public var accent: Color { Color.interactiveActive }
    public var background: Color { Color(.systemBackground) }
    public var secondaryBackground: Color { Color(.secondarySystemBackground) }
    public var tertiaryBackground: Color { Color(.tertiarySystemBackground) }
    public var label: Color { Color(.label) }
    public var secondaryLabel: Color { Color(.secondaryLabel) }
    public var tertiaryLabel: Color { Color(.tertiaryLabel) }
    public var border: Color { Color(.separator) }
    public var link: Color { Color(.link) }
    public var notification: Color { Colors.State.swiftUILikeActive }
    public var success: Color { Colors.State.swiftUIRetweetActive }
    public var warning: Color { Color.yellow }
    public var error: Color { Colors.State.swiftUILikeActive }
}

// Flare theme - light theme with Flare design language
public struct FlareColorSet: ColorSet {
    public var primary: Color { Color.textPrimary }
    public var secondary: Color { Color.textSecondary }
    public var accent: Color { Color.interactiveActive }
    public var background: Color { Color.backgroundPrimary }
    public var secondaryBackground: Color { Color.backgroundSecondary }
    public var tertiaryBackground: Color { Color.backgroundTertiary }
    public var label: Color { Color.textPrimary }
    public var secondaryLabel: Color { Color.textSecondary }
    public var tertiaryLabel: Color { Color.textTertiary }
    public var border: Color { Color.backgroundTertiary }
    public var link: Color { Color.functionLink }
    public var notification: Color { Color.interactiveActive }
    public var success: Color { Colors.State.swiftUIRetweetActive }
    public var warning: Color { Color.yellow }
    public var error: Color { Colors.State.swiftUILikeActive }
}

// Dim theme - dark theme with muted colors
public struct DimColorSet: ColorSet {
    public var primary: Color { Color.textPrimary }
    public var secondary: Color { Color.textSecondary }
    public var accent: Color { Color.interactiveActive }
    public var background: Color { Color.backgroundPrimary }
    public var secondaryBackground: Color { Color.backgroundSecondary }
    public var tertiaryBackground: Color { Color.backgroundTertiary }
    public var label: Color { Color.textPrimary }
    public var secondaryLabel: Color { Color.textSecondary }
    public var tertiaryLabel: Color { Color.textTertiary }
    public var border: Color { Color.backgroundTertiary }
    public var link: Color { Color.functionLink }
    public var notification: Color { Color.interactiveActive }
    public var success: Color { Colors.State.swiftUIRetweetActive }
    public var warning: Color { Color.yellow }
    public var error: Color { Colors.State.swiftUILikeActive }
}

// Classic theme - light theme with classic iOS colors
public struct ClassicColorSet: ColorSet {
    public var primary: Color { Color.textPrimary }
    public var secondary: Color { Color.textSecondary }
    public var accent: Color { Color.interactiveActive }
    public var background: Color { Color.backgroundPrimary }
    public var secondaryBackground: Color { Color.backgroundSecondary }
    public var tertiaryBackground: Color { Color.backgroundTertiary }
    public var label: Color { Color.textPrimary }
    public var secondaryLabel: Color { Color.textSecondary }
    public var tertiaryLabel: Color { Color.textTertiary }
    public var border: Color { Color.backgroundTertiary }
    public var link: Color { Color.functionLink }
    public var notification: Color { Colors.State.swiftUILikeActive }
    public var success: Color { Colors.State.swiftUIRetweetActive }
    public var warning: Color { Color.yellow }
    public var error: Color { Colors.State.swiftUILikeActive }
}

// Additional themes
public struct SnowfallColorSet: ColorSet {
    public var primary: Color { Color(hex: "#293241") }
    public var secondary: Color { Color(hex: "#3D5A80") }
    public var accent: Color { Color(hex: "#5E81AC") }
    public var background: Color { Color(hex: "#F5F5F5") }
    public var secondaryBackground: Color { Color(hex: "#E5E9F0") }
    public var tertiaryBackground: Color { Color(hex: "#D8DEE9") }
    public var label: Color { Color(hex: "#293241") }
    public var secondaryLabel: Color { Color(hex: "#3D5A80") }
    public var tertiaryLabel: Color { Color(hex: "#8FBCBB") }
    public var border: Color { Color(hex: "#D8DEE9") }
    public var link: Color { Color(hex: "#5E81AC") }
    public var notification: Color { Color(hex: "#BF616A") }
    public var success: Color { Color(hex: "#A3BE8C") }
    public var warning: Color { Color(hex: "#EBCB8B") }
    public var error: Color { Color(hex: "#BF616A") }
}

public struct BreezyColorSet: ColorSet {
    public var primary: Color { Color(hex: "#2E3440") }
    public var secondary: Color { Color(hex: "#4C566A") }
    public var accent: Color { Color(hex: "#88C0D0") }
    public var background: Color { Color(hex: "#FFFFFF") }
    public var secondaryBackground: Color { Color(hex: "#F7F7F7") }
    public var tertiaryBackground: Color { Color(hex: "#ECEFF4") }
    public var label: Color { Color(hex: "#2E3440") }
    public var secondaryLabel: Color { Color(hex: "#4C566A") }
    public var tertiaryLabel: Color { Color(hex: "#9198A8") }
    public var border: Color { Color(hex: "#E5E9F0") }
    public var link: Color { Color(hex: "#88C0D0") }
    public var notification: Color { Color(hex: "#BF616A") }
    public var success: Color { Color(hex: "#A3BE8C") }
    public var warning: Color { Color(hex: "#EBCB8B") }
    public var error: Color { Color(hex: "#BF616A") }
}

public struct EmberColorSet: ColorSet {
    public var primary: Color { Color(hex: "#E5E9F0") }
    public var secondary: Color { Color(hex: "#ECEFF4") }
    public var accent: Color { Color(hex: "#BF616A") }
    public var background: Color { Color(hex: "#2E3440") }
    public var secondaryBackground: Color { Color(hex: "#3B4252") }
    public var tertiaryBackground: Color { Color(hex: "#434C5E") }
    public var label: Color { Color(hex: "#E5E9F0") }
    public var secondaryLabel: Color { Color(hex: "#D8DEE9") }
    public var tertiaryLabel: Color { Color(hex: "#B3B9C7") }
    public var border: Color { Color(hex: "#4C566A") }
    public var link: Color { Color(hex: "#BF616A") }
    public var notification: Color { Color(hex: "#BF616A") }
    public var success: Color { Color(hex: "#A3BE8C") }
    public var warning: Color { Color(hex: "#EBCB8B") }
    public var error: Color { Color(hex: "#BF616A") }
}

public struct SunsetColorSet: ColorSet {
    public var primary: Color { Color(hex: "#ECEFF4") }
    public var secondary: Color { Color(hex: "#E5E9F0") }
    public var accent: Color { Color(hex: "#D08770") }
    public var background: Color { Color(hex: "#2E3440") }
    public var secondaryBackground: Color { Color(hex: "#3B4252") }
    public var tertiaryBackground: Color { Color(hex: "#434C5E") }
    public var label: Color { Color(hex: "#ECEFF4") }
    public var secondaryLabel: Color { Color(hex: "#E5E9F0") }
    public var tertiaryLabel: Color { Color(hex: "#D8DEE9") }
    public var border: Color { Color(hex: "#4C566A") }
    public var link: Color { Color(hex: "#D08770") }
    public var notification: Color { Color(hex: "#BF616A") }
    public var success: Color { Color(hex: "#A3BE8C") }
    public var warning: Color { Color(hex: "#EBCB8B") }
    public var error: Color { Color(hex: "#BF616A") }
}

public struct CarbonColorSet: ColorSet {
    public var primary: Color { Color(hex: "#D8DEE9") }
    public var secondary: Color { Color(hex: "#E5E9F0") }
    public var accent: Color { Color(hex: "#81A1C1") }
    public var background: Color { Color(hex: "#1D1D1D") }
    public var secondaryBackground: Color { Color(hex: "#2D2D2D") }
    public var tertiaryBackground: Color { Color(hex: "#3D3D3D") }
    public var label: Color { Color(hex: "#D8DEE9") }
    public var secondaryLabel: Color { Color(hex: "#B3B9C7") }
    public var tertiaryLabel: Color { Color(hex: "#9198A8") }
    public var border: Color { Color(hex: "#3D3D3D") }
    public var link: Color { Color(hex: "#81A1C1") }
    public var notification: Color { Color(hex: "#BF616A") }
    public var success: Color { Color(hex: "#A3BE8C") }
    public var warning: Color { Color(hex: "#EBCB8B") }
    public var error: Color { Color(hex: "#BF616A") }
}

public struct NordColorSet: ColorSet {
    public var primary: Color { Color(hex: "#ECEFF4") }
    public var secondary: Color { Color(hex: "#E5E9F0") }
    public var accent: Color { Color(hex: "#88C0D0") }
    public var background: Color { Color(hex: "#2E3440") }
    public var secondaryBackground: Color { Color(hex: "#3B4252") }
    public var tertiaryBackground: Color { Color(hex: "#434C5E") }
    public var label: Color { Color(hex: "#ECEFF4") }
    public var secondaryLabel: Color { Color(hex: "#E5E9F0") }
    public var tertiaryLabel: Color { Color(hex: "#D8DEE9") }
    public var border: Color { Color(hex: "#4C566A") }
    public var link: Color { Color(hex: "#88C0D0") }
    public var notification: Color { Color(hex: "#BF616A") }
    public var success: Color { Color(hex: "#A3BE8C") }
    public var warning: Color { Color(hex: "#EBCB8B") }
    public var error: Color { Color(hex: "#BF616A") }
}

public struct DraculaColorSet: ColorSet {
    public var primary: Color { Color(hex: "#F8F8F2") }
    public var secondary: Color { Color(hex: "#BFBFBF") }
    public var accent: Color { Color(hex: "#BD93F9") }
    public var background: Color { Color(hex: "#282A36") }
    public var secondaryBackground: Color { Color(hex: "#383A59") }
    public var tertiaryBackground: Color { Color(hex: "#44475A") }
    public var label: Color { Color(hex: "#F8F8F2") }
    public var secondaryLabel: Color { Color(hex: "#BFBFBF") }
    public var tertiaryLabel: Color { Color(hex: "#929292") }
    public var border: Color { Color(hex: "#44475A") }
    public var link: Color { Color(hex: "#BD93F9") }
    public var notification: Color { Color(hex: "#FF5555") }
    public var success: Color { Color(hex: "#50FA7B") }
    public var warning: Color { Color(hex: "#F1FA8C") }
    public var error: Color { Color(hex: "#FF5555") }
}

public struct MinuitColorSet: ColorSet {
    public var primary: Color { Color(hex: "#E5E9F0") }
    public var secondary: Color { Color(hex: "#D8DEE9") }
    public var accent: Color { Color(hex: "#B48EAD") }
    public var background: Color { Color(hex: "#1A1A1A") }
    public var secondaryBackground: Color { Color(hex: "#252525") }
    public var tertiaryBackground: Color { Color(hex: "#303030") }
    public var label: Color { Color(hex: "#E5E9F0") }
    public var secondaryLabel: Color { Color(hex: "#D8DEE9") }
    public var tertiaryLabel: Color { Color(hex: "#B3B9C7") }
    public var border: Color { Color(hex: "#303030") }
    public var link: Color { Color(hex: "#B48EAD") }
    public var notification: Color { Color(hex: "#BF616A") }
    public var success: Color { Color(hex: "#A3BE8C") }
    public var warning: Color { Color(hex: "#EBCB8B") }
    public var error: Color { Color(hex: "#BF616A") }
}

public struct NoirColorSet: ColorSet {
    public var primary: Color { Color(hex: "#FFFFFF") }
    public var secondary: Color { Color(hex: "#D8D8D8") }
    public var accent: Color { Color(hex: "#FFFFFF") }
    public var background: Color { Color(hex: "#000000") }
    public var secondaryBackground: Color { Color(hex: "#101010") }
    public var tertiaryBackground: Color { Color(hex: "#202020") }
    public var label: Color { Color(hex: "#FFFFFF") }
    public var secondaryLabel: Color { Color(hex: "#D8D8D8") }
    public var tertiaryLabel: Color { Color(hex: "#A8A8A8") }
    public var border: Color { Color(hex: "#202020") }
    public var link: Color { Color(hex: "#FFFFFF") }
    public var notification: Color { Color(hex: "#FFFFFF") }
    public var success: Color { Color(hex: "#FFFFFF") }
    public var warning: Color { Color(hex: "#FFFFFF") }
    public var error: Color { Color(hex: "#FFFFFF") }
}
