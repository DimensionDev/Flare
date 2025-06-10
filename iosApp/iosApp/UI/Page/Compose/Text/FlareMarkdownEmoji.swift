
import SwiftUI
import EmojiText

@MainActor
public struct FlareEmojiText: View {
    private let text: String
    private let markdownText: String
    private let emojis: [RemoteEmoji]
    private let style: FlareTextStyle.Style
    private let isRTL: Bool
    private let fontScale: Double
    private let lineSpacing: Double
    private var linkHandler: ((URL) -> Void)?
    @Environment(FlareTheme.self) private var theme

    public init(
        text: String,
        markdownText: String,
        emojis: [RemoteEmoji] = [],
        style: FlareTextStyle.Style,
        isRTL: Bool = false,
        fontScale: Double = 1.0,
        lineSpacing: Double = 1.0
    ) {
        self.text = text
        self.markdownText = markdownText
        self.emojis = emojis
        self.style = style
        self.isRTL = isRTL
        self.fontScale = fontScale
        self.lineSpacing = lineSpacing
    }
    
    public func onLinkTap(_ handler: @escaping (URL) -> Void) -> FlareEmojiText {
        var view = self
        view.linkHandler = handler
        return view
    }
    
    public var body: some View {
            EmojiText(markdown: markdownText.replacingOccurrences(
                    of: "<br\\s*/?>", 
                    with: "\n", 
                    options: [.regularExpression, .caseInsensitive]
                ), emojis: emojis)
            .markdownTheme(.flareMarkdownStyle(using: style, fontScale: theme.fontSizeScale))
//                .font(style.font.scaled(by: fontScale))
//                .foregroundColor(style.color)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .relativeLineSpacing(.em(lineSpacing - 1.0))
                .environment(\.layoutDirection, isRTL ? .rightToLeft : .leftToRight)
                .environment(\.openURL, OpenURLAction { url in
                    if let handler = linkHandler {
                        handler(url)
                        return .handled
                    } else {
                        return .systemAction
                    }
                })
        }
}
  
