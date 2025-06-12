import EmojiText
import MarkdownUI
import SwiftUI
import TwitterText

public struct FlareText: View {
    private let text: String
    private let isRTL: Bool
    private let markdownText: String
    private let style: FlareTextStyle.Style
    private var linkHandler: ((URL) -> Void)?
    @Environment(FlareTheme.self) private var theme
    @Environment(\.appSettings) private var appSettings

    @State private var cacheKey: String = ""

    public init(
        _ text: String,
        _ markdownText: String,
        style: FlareTextStyle.Style,
        isRTL: Bool = false
    ) {
        self.text = text
        self.markdownText = markdownText
        self.style = style
        self.isRTL = isRTL
    }

    public func onLinkTap(_ handler: @escaping (URL) -> Void) -> FlareText {
        var view = self
        view.linkHandler = handler
        return view
    }

    @ViewBuilder
    private var optimizedBody: some View {
        switch appSettings.appearanceSettings.renderEngine {
        case .markdown:
            MarkdownRenderer()
        case .flareText:
            FlareTextRenderer()
        case .textViewMarkdown:
            TextViewMarkdownRenderer()
        case .emojiText:
            EmojiTextRenderer()
        }
    }

    @ViewBuilder
    private func MarkdownRenderer() -> some View {
        Markdown(markdownText)
            .markdownTheme(.flareMarkdownStyle(using: style, fontScale: theme.fontSizeScale))
            .markdownInlineImageProvider(.emoji)
            .relativeLineSpacing(.em(theme.lineSpacing - 1.0)) // 转换为相对行高
            .padding(.vertical, 4)
            .environment(\.layoutDirection, isRTL ? .rightToLeft : .leftToRight)
            .environment(\.openURL, linkOpenURLAction)
    }

    @ViewBuilder
    private func FlareTextRenderer() -> some View {
        let currentCacheKey = FlareTextCache.shared.generateCacheKey(
            text: text,
            markdownText: markdownText,
            style: style,
            renderEngine: appSettings.appearanceSettings.renderEngine
        )
        Text(AttributedString(processText(text, markdownText)))
            .multilineTextAlignment(.leading)
            .fixedSize(horizontal: false, vertical: true)
            // .environment(
            //     \.openURL,
            //     OpenURLAction { url in
            //         if let handler = linkHandler {
            //             handler(url)
            //         }
            //         return .handled
            //     }
            // )
            .environment(\.openURL, linkOpenURLAction)
            .environment(\.layoutDirection, isRTL ? .rightToLeft : .leftToRight)
            .onAppear {
                cacheKey = currentCacheKey
            }
            .onChange(of: currentCacheKey) { newKey in
                cacheKey = newKey
            }
    }

    @ViewBuilder
    private func TextViewMarkdownRenderer() -> some View {
        TextViewMarkdown(
            markdownText: markdownText,
            style: style,
            fontScale: theme.fontSizeScale
        )
    }

    @ViewBuilder
    private func EmojiTextRenderer() -> some View {
        FlareEmojiText(
            text: text,
            markdownText: markdownText,
            emojis: [],
            style: style,
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

    public var body: some View {
        optimizedBody
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

    private func processText(_ text: String, _ markdownText: String) -> NSAttributedString {
        let attributedString = FlareTextStyle.attributeString(
            of: text,
            markdownText: markdownText,
            style: style
        )
        return NSAttributedString(attributedString)
    }

    private func getCachedOrProcessText(cacheKey: String) -> NSAttributedString {
        if let cached = FlareTextCache.shared.getCachedText(for: cacheKey) {
            return cached
        }

        let attributedString = FlareTextStyle.attributeString(
            of: text,
            markdownText: markdownText,
            style: style
        )
        let nsAttributedString = NSAttributedString(attributedString)

        FlareTextCache.shared.setCachedText(nsAttributedString, for: cacheKey)

        return nsAttributedString
    }
}
