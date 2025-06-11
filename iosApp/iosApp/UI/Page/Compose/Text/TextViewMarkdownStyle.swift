import MarkdownView
import UIKit

public enum TextViewMarkdownStyle {
    public static func TextViewMarkdownStyleTheme(using style: FlareTextStyle.Style, fontScale: Double) -> MarkdownTheme {
        // 获取基础字体大小
        let baseFontSize = style.font.pointSize
        // 应用用户的字体缩放设置
        let scaledFontSize = baseFontSize * fontScale

        var theme = MarkdownTheme()

        // Fonts
        theme.fonts.body = style.font.withSize(scaledFontSize)
        theme.fonts.codeInline = UIFont.monospacedSystemFont(ofSize: scaledFontSize, weight: .regular)

        // 尝试获取粗体
        var boldFontDescriptor = style.font.fontDescriptor.withSymbolicTraits(.traitBold)
        if let boldFontDesc = boldFontDescriptor {
            theme.fonts.bold = UIFont(descriptor: boldFontDesc, size: scaledFontSize)
        } else {
            // 回退到系统粗体
            theme.fonts.bold = UIFont.systemFont(ofSize: scaledFontSize, weight: .bold)
        }

        // 尝试获取斜体
        var italicFontDescriptor = style.font.fontDescriptor.withSymbolicTraits(.traitItalic)
        if let italicFontDesc = italicFontDescriptor {
            theme.fonts.italic = UIFont(descriptor: italicFontDesc, size: scaledFontSize)
        } else {
            // 回退到系统斜体
            theme.fonts.italic = UIFont.italicSystemFont(ofSize: scaledFontSize)
        }

        theme.fonts.code = UIFont.monospacedSystemFont(ofSize: ceil(scaledFontSize * MarkdownTheme.codeScale), weight: .regular)

        // MarkdownTheme 还有 largeTitle, title, footnote，这里暂时使用 body 的 scaledFontSize
        // 如果 FlareTextStyle.Style 将来有更细致的定义，可以再调整
        var largeTitleFontDescriptor = style.font.fontDescriptor.withSymbolicTraits(.traitBold)
        if let largeTitleDesc = largeTitleFontDescriptor {
            theme.fonts.largeTitle = UIFont(descriptor: largeTitleDesc, size: scaledFontSize * 1.2) // 稍微大一点
        } else {
            theme.fonts.largeTitle = UIFont.systemFont(ofSize: scaledFontSize * 1.2, weight: .bold)
        }

        var titleFontDescriptor = style.font.fontDescriptor.withSymbolicTraits(.traitBold)
        if let titleDesc = titleFontDescriptor {
            theme.fonts.title = UIFont(descriptor: titleDesc, size: scaledFontSize * 1.1) // 稍微大一点
        } else {
            theme.fonts.title = UIFont.systemFont(ofSize: scaledFontSize * 1.1, weight: .bold)
        }
        theme.fonts.footnote = style.font.withSize(scaledFontSize * 0.8) // 小一点

        // Colors
        theme.colors.body = style.textColor
        theme.colors.highlight = style.linkColor //  MarkdownView 的 highlight 对应链接颜色
        theme.colors.emphasis = style.linkColor // MarkdownView 的 emphasis 也用链接颜色或 Flare 主题强调色
        theme.colors.code = style.textColor
        theme.colors.codeBackground = UIColor.gray.withAlphaComponent(0.1) // 保持与 MarkdownUI 一致的背景色

        // Spacings & Sizes 可以暂时使用 MarkdownTheme 的默认值，或根据 Flare 设计规范调整
        // theme.spacings = ...
        // theme.sizes = ...

        return theme
    }
}

// MARK: - FlareTextStyle.Style Extension

extension FlareTextStyle.Style {
    func isEqual(to other: FlareTextStyle.Style) -> Bool {
        font.isEqual(other.font) &&
            textColor.isEqual(other.textColor) &&
            linkColor.isEqual(other.linkColor)
    }
}
