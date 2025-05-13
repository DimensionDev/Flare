import SwiftUI

public enum FontType: String, CaseIterable, Identifiable, Codable {
    case system = "System"
    case rounded = "SF Rounded"
    case serif = "New York"
    case mono = "SF Mono"
    
    public var id: String { self.rawValue }
}

public class AppFontProvider: ObservableObject {
    // The shared instance used throughout the app
    public static let shared = AppFontProvider()
    
    @AppStorage("flare-font-type") public var selectedFontType: FontType = .system
    
    // Font weight stored as string
    @AppStorage("flare-font-weight") public var fontWeightString: String = "regular"
    
    // Font size multiplier
    @AppStorage("flare-font-size-multiplier") public var fontSizeMultiplier: Double = 1.0
    
    // Computed property to convert string to Font.Weight
    public var selectedFontWeight: Font.Weight {
        switch fontWeightString {
        case "thin": return .thin
        case "ultraLight": return .ultraLight
        case "light": return .light
        case "regular": return .regular
        case "medium": return .medium
        case "semibold": return .semibold
        case "bold": return .bold
        case "heavy": return .heavy
        case "black": return .black
        default: return .regular
        }
    }
    
    private init() {}
    
    // 自定义字体更新方法
    public func updateFont(name: String, size: CGFloat) {
        // 根据字体名称确定字体类型
        if name.contains("SFUI") || name == ".SFUI-Regular" {
            selectedFontType = .system
        } else if name.contains("SFRounded") {
            selectedFontType = .rounded
        } else if name.contains("NewYork") {
            selectedFontType = .serif
        } else if name.contains("SFMono") {
            selectedFontType = .mono
        }
        
        // 计算并设置字体大小倍数
        fontSizeMultiplier = size / 14.0
        
        // 通知观察者字体已更新
        objectWillChange.send()
    }
    
    public func font(for textStyle: Font.TextStyle, design: Font.Design? = nil, weight: Font.Weight? = nil) -> Font {
        let fontWeight = weight ?? selectedFontWeight
        let multiplier = CGFloat(fontSizeMultiplier)
        
        // Get the system default size for the text style
        let uiFontTextStyle: UIFont.TextStyle
        switch textStyle {
        case .largeTitle: uiFontTextStyle = .largeTitle
        case .title: uiFontTextStyle = .title1
        case .title2: uiFontTextStyle = .title2
        case .title3: uiFontTextStyle = .title3
        case .headline: uiFontTextStyle = .headline
        case .subheadline: uiFontTextStyle = .subheadline
        case .body: uiFontTextStyle = .body
        case .callout: uiFontTextStyle = .callout
        case .footnote: uiFontTextStyle = .footnote
        case .caption: uiFontTextStyle = .caption1
        case .caption2: uiFontTextStyle = .caption2
        default: uiFontTextStyle = .body
        }
        
        let defaultSize = UIFont.preferredFont(forTextStyle: uiFontTextStyle).pointSize
        let scaledSize = defaultSize * multiplier
        
        switch selectedFontType {
        case .system:
            if let design = design {
                return Font.system(size: scaledSize, weight: fontWeight, design: design)
            } else {
                return Font.system(size: scaledSize, weight: fontWeight)
            }
        case .rounded:
            return Font.system(size: scaledSize, weight: fontWeight, design: .rounded)
        case .serif:
            return Font.system(size: scaledSize, weight: fontWeight, design: .serif)
        case .mono:
            return Font.system(size: scaledSize, weight: fontWeight, design: .monospaced)
        }
    }
    
    // Helper methods for common text styles
    public var largeTitle: Font { font(for: .largeTitle) }
    public var title: Font { font(for: .title) }
    public var title2: Font { font(for: .title2) }
    public var title3: Font { font(for: .title3) }
    public var headline: Font { font(for: .headline) }
    public var subheadline: Font { font(for: .subheadline) }
    public var body: Font { font(for: .body) }
    public var callout: Font { font(for: .callout) }
    public var footnote: Font { font(for: .footnote) }
    public var caption: Font { font(for: .caption) }
    public var caption2: Font { font(for: .caption2) }
}

// Font extensions for easier use in the app
public extension View {
    func flareFont(_ textStyle: Font.TextStyle, weight: Font.Weight? = nil) -> some View {
        self.font(AppFontProvider.shared.font(for: textStyle, weight: weight))
    }
} 