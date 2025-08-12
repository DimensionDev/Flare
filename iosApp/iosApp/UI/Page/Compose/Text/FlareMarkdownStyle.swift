import MarkdownUI
import SwiftUI

extension MarkdownUI.Theme {
    static func flareMarkdownStyle(using style: FlareTextStyle.Style, fontScale: Double) -> MarkdownUI.Theme {
        let baseFontSize = style.font.pointSize

        let scaledFontSize = baseFontSize * fontScale

        return MarkdownUI.Theme()
            .text {
                FontSize(scaledFontSize)
                ForegroundColor(Color(style.textColor))

                if let fontName = style.font.fontName as String?, fontName != ".SFUI-Regular" {
                    FontFamily(.custom(fontName))
                }
            }
            .link {
                ForegroundColor(Color(style.linkColor))
                // UnderlineStyle(.single)
            }
            .strong {
                FontWeight(.semibold)
                ForegroundColor(Color(style.textColor))
            }
            .emphasis {
                FontStyle(.italic)
                ForegroundColor(Color(style.textColor))
            }
            .code {
                FontFamilyVariant(.monospaced)
                FontSize(scaledFontSize * 0.9)
                ForegroundColor(Color(style.textColor))
            }
    }
}
