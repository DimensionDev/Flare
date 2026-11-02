import SwiftUI
import KotlinSharedUI

#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

public extension Color {
    static var flareSystemBackground: Color {
        #if os(iOS)
        Color(.systemBackground)
        #elseif os(macOS)
        Color(nsColor: .windowBackgroundColor)
        #endif
    }

    static var flareSystemGroupedBackground: Color {
        #if os(iOS)
        Color(.systemGroupedBackground)
        #elseif os(macOS)
        Color(nsColor: .windowBackgroundColor)
        #endif
    }

    static var flareSecondarySystemBackground: Color {
        #if os(iOS)
        Color(.secondarySystemBackground)
        #elseif os(macOS)
        Color(nsColor: .alternatingContentBackgroundColors[1])
        #endif
    }

    static var flareSecondarySystemGroupedBackground: Color {
        #if os(iOS)
        Color(.secondarySystemGroupedBackground)
        #elseif os(macOS)
        Color(nsColor: .controlBackgroundColor)
        #endif
    }

    static var flareTertiarySystemGroupedBackground: Color {
        #if os(iOS)
        Color(.tertiarySystemGroupedBackground)
        #elseif os(macOS)
        Color(nsColor: .alternatingContentBackgroundColors[1])
        #endif
    }

    static var flareSeparator: Color {
        #if os(iOS)
        Color(.separator)
        #elseif os(macOS)
        Color(nsColor: .separatorColor)
        #endif
    }

    static var flareLabel: Color {
        #if os(iOS)
        Color(.label)
        #elseif os(macOS)
        Color(nsColor: .labelColor)
        #endif
    }

    static var flareSecondaryLabel: Color {
        #if os(iOS)
        Color(.secondaryLabel)
        #elseif os(macOS)
        Color(nsColor: .secondaryLabelColor)
        #endif
    }
}

public extension ActionMenu.ItemColor {
    var swiftColor: Color? {
        switch self {
        case .red: return .red
        case .contentColor: return nil
        case .primaryColor: return StatusActionColors.primary
        }
    }

    #if os(iOS)
    var uiColor: UIColor? {
        switch self {
        case .red: return .systemRed
        case .contentColor: return nil
        case .primaryColor: return StatusActionColors.primaryUIColor
        }
    }
    #endif
}

private enum StatusActionColors {
    static let primary = Color(red: 0, green: 186.0 / 255, blue: 124.0 / 255)
    #if os(iOS)
    static let primaryUIColor = UIColor(primary)
    #endif
}
