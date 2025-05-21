import SwiftUI
import TwitterText

/// A SwiftUI view that displays processed text with Twitter-style formatting
public struct FlareText: View {
    private let text: String
    private let twitterTextProvider: TwitterTextProvider
    private let style: FlareMarkdownText.Style
    private var linkHandler: ((URL) -> Void)?
    @Environment(FlareTheme.self) private var theme

    public init(
        _ text: String,
        twitterTextProvider: TwitterTextProvider = SwiftTwitterTextProvider(),
        style: FlareMarkdownText.Style = FlareMarkdownText.Style(
            font: .systemFont(ofSize: 16),
            textColor: UIColor.black,
            linkColor: UIColor.black,
            mentionColor: UIColor.black,
            hashtagColor: UIColor.black,
            cashtagColor: UIColor.black
        )
    ) {
        self.text = text
        self.twitterTextProvider = twitterTextProvider
        self.style = style
    }

    public func onLinkTap(_ handler: @escaping (URL) -> Void) -> FlareText {
        var view = self
        view.linkHandler = handler
        return view
    }

    public var body: some View {
        Text(AttributedString(processText(text)))
            .multilineTextAlignment(.leading)
            .fixedSize(horizontal: false, vertical: true)
            .environment(\.openURL, OpenURLAction { url in
                if let handler = linkHandler {
                    handler(url)
                }
                return .handled
            })
    }

    private func processText(_ text: String) -> NSAttributedString {
        let (attributedString, _, _) = FlareMarkdownText.attributeString(
            of: text,
            style: FlareMarkdownText.Style(
                font: .systemFont(ofSize: 16),
                textColor: UIColor(theme.labelColor),
                linkColor: UIColor(theme.tintColor),
                mentionColor: UIColor(theme.tintColor),
                hashtagColor: UIColor(theme.tintColor),
                cashtagColor: UIColor(theme.tintColor)
            )
        )
        return NSAttributedString(attributedString)
    }
}
