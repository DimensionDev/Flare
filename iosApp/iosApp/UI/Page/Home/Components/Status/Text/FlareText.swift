import SwiftUI
import TwitterText

/// A SwiftUI view that displays processed text with Twitter-style formatting
public struct FlareText: View {
    private let text: String
    private let twitterTextProvider: TwitterTextProvider
    private let style: FlareMarkdownText.Style
    private var linkHandler: ((URL) -> Void)?
    
    public init(
        _ text: String,
        twitterTextProvider: TwitterTextProvider = SwiftTwitterTextProvider(),
        style: FlareMarkdownText.Style = .default
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
            style: style
        )
        return NSAttributedString(attributedString)
    }
}

#if DEBUG
struct FlareText_Previews: PreviewProvider {
    static var previews: some View {
        VStack(alignment: .leading, spacing: 20) {
            FlareText("Hello @user! Check out #swiftui and $AAPL")
            
            FlareText(
                "Custom style example",
                style: .init(
                    font: .systemFont(ofSize: 18, weight: .medium),
                    textColor: .darkText,
                    linkColor: .systemPurple,
                    mentionColor: .systemGreen,
                    hashtagColor: .systemOrange,
                    cashtagColor: .systemRed
                )
            )
            
            FlareText("https://example.com and **bold** text")
        }
        .padding()
    }
}
#endif
