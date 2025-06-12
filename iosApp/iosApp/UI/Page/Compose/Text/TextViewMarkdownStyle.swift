import MarkdownView
import UIKit

public enum TextViewMarkdownStyle {
    public static func TextViewMarkdownStyleTheme(using style: FlareTextStyle.Style, fontScale: Double) -> MarkdownTheme {
        let baseFontSize = style.font.pointSize

        let scaledFontSize = baseFontSize * fontScale

        var theme = MarkdownTheme()

        theme.fonts.body = style.font.withSize(scaledFontSize)
        theme.fonts.codeInline = UIFont.monospacedSystemFont(ofSize: scaledFontSize, weight: .regular)

        var boldFontDescriptor = style.font.fontDescriptor.withSymbolicTraits(.traitBold)
        if let boldFontDesc = boldFontDescriptor {
            theme.fonts.bold = UIFont(descriptor: boldFontDesc, size: scaledFontSize)
        } else {
            theme.fonts.bold = UIFont.systemFont(ofSize: scaledFontSize, weight: .bold)
        }

        var italicFontDescriptor = style.font.fontDescriptor.withSymbolicTraits(.traitItalic)
        if let italicFontDesc = italicFontDescriptor {
            theme.fonts.italic = UIFont(descriptor: italicFontDesc, size: scaledFontSize)
        } else {
            theme.fonts.italic = UIFont.italicSystemFont(ofSize: scaledFontSize)
        }

        theme.fonts.code = UIFont.monospacedSystemFont(ofSize: ceil(scaledFontSize * MarkdownTheme.codeScale), weight: .regular)

        var largeTitleFontDescriptor = style.font.fontDescriptor.withSymbolicTraits(.traitBold)
        if let largeTitleDesc = largeTitleFontDescriptor {
            theme.fonts.largeTitle = UIFont(descriptor: largeTitleDesc, size: scaledFontSize * 1.2)
        } else {
            theme.fonts.largeTitle = UIFont.systemFont(ofSize: scaledFontSize * 1.2, weight: .bold)
        }

        var titleFontDescriptor = style.font.fontDescriptor.withSymbolicTraits(.traitBold)
        if let titleDesc = titleFontDescriptor {
            theme.fonts.title = UIFont(descriptor: titleDesc, size: scaledFontSize * 1.1)
        } else {
            theme.fonts.title = UIFont.systemFont(ofSize: scaledFontSize * 1.1, weight: .bold)
        }
        theme.fonts.footnote = style.font.withSize(scaledFontSize * 0.8)

        // Colors
        theme.colors.body = style.textColor
        theme.colors.highlight = style.linkColor
        theme.colors.emphasis = style.linkColor
        theme.colors.code = style.textColor
        theme.colors.codeBackground = UIColor.gray.withAlphaComponent(0.1)

        return theme
    }
}

extension FlareTextStyle.Style {
    func isEqual(to other: FlareTextStyle.Style) -> Bool {
        font.isEqual(other.font) &&
            textColor.isEqual(other.textColor) &&
            linkColor.isEqual(other.linkColor)
    }
}
