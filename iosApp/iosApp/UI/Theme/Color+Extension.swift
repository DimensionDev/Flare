import SwiftUI

extension SwiftUI.Color {
    // - Background Colors

    static var backgroundPrimary: SwiftUI.Color {
        Colors.Background.swiftUIPrimary
    }

    static var backgroundSecondary: SwiftUI.Color {
        Colors.Background.swiftUISecondary
    }

    static var backgroundTertiary: SwiftUI.Color {
        Colors.Background.swiftUITertiary
    }

    // - Text Colors

    static var textPrimary: SwiftUI.Color {
        Colors.Text.swiftUIPrimary
    }

    static var textSecondary: SwiftUI.Color {
        Colors.Text.swiftUISecondary
    }

    static var textTertiary: SwiftUI.Color {
        Colors.Text.swiftUITertiary
    }

    // - Interactive Colors

    static var interactiveActive: SwiftUI.Color {
        Colors.State.swiftUIActive
    }

    static var interactiveInactive: SwiftUI.Color {
        Colors.State.swiftUIDeactive
    }

    static var interactiveDisabled: SwiftUI.Color {
        Colors.State.swiftUIDeactiveDarker
    }

    // - Function Colors

    static var functionLink: SwiftUI.Color {
        Colors.Link.swiftUIHyperlink
    }

    static var functionMention: SwiftUI.Color {
        Colors.Link.swiftUIMention
    }

    static var functionHashtag: SwiftUI.Color {
        Colors.Link.swiftUIHashtag
    }

    static var functionCashtag: SwiftUI.Color {
        Colors.Link.swiftUICashtag
    }

    // - Theme扩展

    /// 从十六进制字符串初始化颜色
    /// - Parameter hexString: 十六进制颜色字符串，形如"#RRGGBB"或"#RRGGBBAA"
    public init?(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (r, g, b, a) = ((int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17, 255)
        case 6: // RGB (24-bit)
            (r, g, b, a) = (int >> 16, int >> 8 & 0xFF, int & 0xFF, 255)
        case 8: // RGBA (32-bit)
            (r, g, b, a) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            return nil
        }

        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }

    /// 将颜色转为十六进制字符串
    /// - Returns: 十六进制颜色字符串，形如"#RRGGBB"
    public func toHex() -> String {
        guard let components = UIColor(self).cgColor.components, components.count >= 3 else {
            return "#000000"
        }
        let r = Float(components[0])
        let g = Float(components[1])
        let b = Float(components[2])
        return String(format: "#%02lX%02lX%02lX", lroundf(r * 255), lroundf(g * 255), lroundf(b * 255))
    }
}
