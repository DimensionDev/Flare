import EmojiText
import MarkdownUI
import SwiftUI
import TwitterText

public enum FlareTextType {
    case body
    case caption
}

public struct FlareText: View, Equatable {
    private let text: String
    private let isRTL: Bool
    private let markdownText: String
    private let textType: FlareTextType
    private var linkHandler: ((URL) -> Void)?
    @Environment(FlareTheme.self) private var theme
    @Environment(\.appSettings) private var appSettings

    public static func == (lhs: FlareText, rhs: FlareText) -> Bool {
        lhs.text == rhs.text &&
            lhs.markdownText == rhs.markdownText &&
            lhs.textType == rhs.textType &&
            lhs.isRTL == rhs.isRTL
        // 注意：linkHandler是函数类型，无法比较，但通常不影响渲染
        // Environment变量由SwiftUI自动处理
    }

    public init(
        _ text: String,
        _ markdownText: String,
        textType: FlareTextType,
        isRTL: Bool = false
    ) {
        self.text = text
        self.markdownText = markdownText
        self.textType = textType
        self.isRTL = isRTL
    }

    public func onLinkTap(_ handler: @escaping (URL) -> Void) -> FlareText {
        var view = self
        view.linkHandler = handler
        return view
    }

    public var body: some View {
        let currentStyle: FlareTextStyle.Style = switch textType {
        case .body:
            theme.bodyTextStyle
        case .caption:
            theme.captionTextStyle
        }

        switch appSettings.appearanceSettings.renderEngine {
        case .markdown:
            Markdown(markdownText)
                .markdownTheme(.flareMarkdownStyle(using: currentStyle, fontScale: theme.fontSizeScale))
                .markdownInlineImageProvider(.emoji)
                .relativeLineSpacing(.em(theme.lineSpacing - 1.0)) // 转换为相对行高
                .padding(.vertical, 4)
                .environment(\.layoutDirection, isRTL ? .rightToLeft : .leftToRight)
                .environment(\.openURL, linkOpenURLAction)
        case .emojiText:
            FlareEmojiText(
                text: text,
                markdownText: markdownText,
                emojis: [],
                style: currentStyle,
                isRTL: isRTL,
                fontScale: theme.fontSizeScale,
                lineSpacing: theme.lineSpacing
            )
            // .environment(\.openURL, linkOpenURLAction)
            .onLinkTap { url in
                if let handler = linkHandler {
                    handler(url)
                }
            }
        }
    }

    private var linkOpenURLAction: OpenURLAction {
        OpenURLAction { url in
            if let handler = linkHandler {
                handler(url)
                return .handled
            } else {
                return .systemAction
            }
        }
    }
}
