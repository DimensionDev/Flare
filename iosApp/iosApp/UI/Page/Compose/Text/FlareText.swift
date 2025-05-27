import SwiftUI
import TwitterText

public struct FlareText: View {
    private let text: String
    private let markdownText: String
    private let style: FlareTextStyle.Style
    private var linkHandler: ((URL) -> Void)?
    @Environment(FlareTheme.self) private var theme

    public init(
        _ text: String,
        _ markdownText: String,
        style: FlareTextStyle.Style = FlareTextStyle.Style(
            font: .systemFont(ofSize: 16),
            textColor: UIColor.black,
            linkColor: UIColor.black,
            mentionColor: UIColor.black,
            hashtagColor: UIColor.black,
            cashtagColor: UIColor.black
        )
    ) {
        self.text = text
        self.markdownText = markdownText
        self.style = style
    }

    public func onLinkTap(_ handler: @escaping (URL) -> Void) -> FlareText {
        var view = self
        view.linkHandler = handler
        return view
    }

    public var body: some View {
        Text(AttributedString(processText(text, markdownText)))
            .multilineTextAlignment(.leading)
            .fixedSize(horizontal: false, vertical: true)
            .environment(\.openURL, OpenURLAction { url in
                if let handler = linkHandler {
                    handler(url)
                }
                return .handled
            })
//            .environment(\.layoutDirection, isRTL() ? .rightToLeft : .leftToRight)
    }

    private func processText(_ text: String, _ markdownText: String) -> NSAttributedString {
        let (attributedString, _, _) = FlareTextStyle.attributeString(
            of: text,
            markdownText: markdownText,
            style: FlareTextStyle.Style(
                font: Font.scaledBodyFont,
                textColor: UIColor(theme.labelColor),
                linkColor: UIColor(theme.tintColor),
                mentionColor: UIColor(theme.tintColor),
                hashtagColor: UIColor(theme.tintColor),
                cashtagColor: UIColor(theme.tintColor)
            )
        )
        return NSAttributedString(attributedString)
    }

//    private func isRTL() -> Bool {
//        // Arabic, Hebrew, Persian, Urdu, Kurdish, Azeri, Dhivehi
//        ["ar", "he", "fa", "ur", "ku", "az", "dv"].contains(language)
//      }
}
