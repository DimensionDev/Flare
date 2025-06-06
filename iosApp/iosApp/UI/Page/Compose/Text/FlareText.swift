import MarkdownUI
import SwiftUI
import TwitterText

public struct FlareText: View {
    private let text: String
    private let lang: String?
    private let markdownText: String
    private let style: FlareTextStyle.Style
    private var linkHandler: ((URL) -> Void)?
    @Environment(FlareTheme.self) private var theme
    @Environment(\.appSettings) private var appSettings

    public init(
        _ text: String,
        _ markdownText: String,
        style: FlareTextStyle.Style,
        lang: String? = nil
    ) {
        self.text = text
        self.markdownText = markdownText
        self.style = style
        self.lang = lang
    }

    public func onLinkTap(_ handler: @escaping (URL) -> Void) -> FlareText {
        var view = self
        view.linkHandler = handler
        return view
    }

    public var body: some View {
        if appSettings.appearanceSettings.renderEngine == .markdown {
            Markdown(markdownText)
                .markdownTheme(.flareMarkdownStyle(using: style, fontScale: theme.fontSizeScale))
                .markdownInlineImageProvider(.emoji)
                .relativeLineSpacing(.em(theme.lineSpacing - 1.0)) // 转换为相对行高
                .padding(.vertical, 4)
                .environment(\.layoutDirection, isRTL() ? .rightToLeft : .leftToRight)
                .environment(\.openURL, OpenURLAction { url in
                    if let handler = linkHandler {
                        handler(url)
                        return .handled
                    } else {
                        return .systemAction
                    }
                })
        } else {
            Text(AttributedString(processText(text, markdownText)))
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .environment(\.openURL, OpenURLAction { url in
                    if let handler = linkHandler {
                        handler(url)
                    }
                    return .handled
                })
                .environment(\.layoutDirection, isRTL() ? .rightToLeft : .leftToRight)
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

    private func isRTL() -> Bool {
        if lang == "" || lang == nil {
            return false
        }
        // let language = Locale.current.language.languageCode?.identifier ?? ""
        let isRTL: Bool = ["ar", "fa", "he", "iw", "ps", "ur"].contains(lang)
        return isRTL
    }
}
