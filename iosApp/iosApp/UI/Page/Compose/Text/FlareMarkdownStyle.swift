import MarkdownUI
import SwiftUI

//  扩展的是 MarkdownUI.Theme
extension MarkdownUI.Theme {
    static func flareMarkdownStyle(using style: FlareTextStyle.Style, fontScale: Double) -> MarkdownUI.Theme {
        // 获取基础字体大小
        let baseFontSize = style.font.pointSize
        // 应用用户的字体缩放设置
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
                // 粗体文本
                FontWeight(.semibold)
                ForegroundColor(Color(style.textColor))
            }
            .emphasis {
                // 斜体文本
                FontStyle(.italic)
                ForegroundColor(Color(style.textColor))
            }
            .code {
                // 行内代码
                FontFamilyVariant(.monospaced)
                FontSize(scaledFontSize * 0.9)
                ForegroundColor(Color(style.textColor))
                // BackgroundColor(Color.gray.opacity(0.1))
            }
    }
}
