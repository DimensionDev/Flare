import Foundation
import SwiftUI
import UIKit

extension FlareTextStyle {
    struct Entity {
        let type: LinkType
        let range: NSRange
    }
}
private extension String { 
    func squashingNewlines() -> String {
        replacingOccurrences(
            of: "\\n\\s*\\n",
            with: "\n",
            options: .regularExpression
        )
    }
}

public enum FlareTextStyle {
      public struct Style {
        public let font: UIFont
        public let textColor: UIColor
        public let linkColor: UIColor
        public let mentionColor: UIColor
        public let hashtagColor: UIColor
        public let cashtagColor: UIColor

        // public static let `default` = Style(
        //     font: .systemFont(ofSize: 16),
        //     textColor: UIColor.black, // .Text.primary,
        //     linkColor: UIColor.black,
        //     mentionColor: UIColor.black,
        //     hashtagColor: UIColor.black,
        //     cashtagColor: UIColor.black
        // )

        // public static let timeline = Style(
        //     font: .systemFont(ofSize: 16),
        //     textColor: UIColor.black,
        //     linkColor: UIColor.black,
        //     mentionColor: UIColor.black,
        //     hashtagColor: UIColor.black,
        //     cashtagColor: UIColor.black
        // )

        // public static let quote = Style(
        //     font: .systemFont(ofSize: 15),
        //     textColor: UIColor.black,
        //     linkColor: UIColor.black.withAlphaComponent(0.8),
        //     mentionColor: UIColor.black.withAlphaComponent(0.8),
        //     hashtagColor: UIColor.black.withAlphaComponent(0.8),
        //     cashtagColor: UIColor.black.withAlphaComponent(0.8)
        // )

        public init(
            font: UIFont = .systemFont(ofSize: 16),
            textColor: UIColor = UIColor.black,
            linkColor: UIColor = UIColor.black,
            mentionColor: UIColor = UIColor.black,
            hashtagColor: UIColor = UIColor.black,
            cashtagColor: UIColor = UIColor.black
        ) {
            self.font = font
            self.textColor = textColor
            self.linkColor = linkColor
            self.mentionColor = mentionColor
            self.hashtagColor = hashtagColor
            self.cashtagColor = cashtagColor
        }
    }

    public enum LinkType {
        case url(URL)
        case mention(String)
        case hashtag(String)
        case cashtag(String)
        case luckyDrop
        case web3

        var color: (Style) -> UIColor {
            switch self {
            case .url: { $0.linkColor }
            case .mention: { $0.mentionColor }
            case .hashtag: { $0.hashtagColor }
            case .cashtag: { $0.cashtagColor }
            case .luckyDrop: { $0.linkColor }
            case .web3: { $0.linkColor }
            }
        }
    }

   
    public static func attributeString(
        of originalText: String?,
        markdownText: String?, // Add markdownText parameter
        prependLinks _: [String: URL] = [:],
        embeds _: [String] = [],
        style: Style,
        previewLinkValidator _: @escaping (String) -> Bool = { _ in false }
    ) ->   AttributedString
     {
 
        guard let originalText, !originalText.isEmpty else {
            return AttributedString() // withOutMediaUrls.last, nil
        }

        let text = originalText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .squashingNewlines()
            .replacingOccurrences(of: "~~", with: " ~~ ")

        let attributedString = {
            do {
                return try AttributedString(
                    markdown: text,
                    options: .init(
                        allowsExtendedAttributes: false,
                        interpretedSyntax: .inlineOnlyPreservingWhitespace,
                        failurePolicy: .returnPartiallyParsedIfPossible,
                        languageCode: nil
                    )
                )
            } catch {
                return AttributedString(text)
            }
        }()

        let nsAttrString = NSMutableAttributedString(attributedString)
        let length = nsAttrString.length

        // 确保文本长度有效
        guard length > 0 else { return AttributedString() } // withOutMediaUrls.last, nil

        // Apply default style
        let fullRange = NSRange(location: 0, length: length)
        nsAttrString.addAttributes(
            [
                .font: style.font,
                .foregroundColor: style.textColor,
            ],
            range: fullRange
        )

       
        let entities = parseEntities(from: text)
        let markdownLinks = parseMarkdownLinks(from: markdownText ?? "")  

        for entity in entities {
            // 验证范围是否有效
            let isValidRange = entity.range.location >= 0 &&
                entity.range.length > 0 &&
                (entity.range.location + entity.range.length) <= length

            guard isValidRange else { continue }

            var attributes: [NSAttributedString.Key: Any] = [
                .font: style.font,
            ]

            switch entity.type {
            case let .url(url):
                attributes[.link] = url
                attributes[.foregroundColor] = entity.type.color(style)
            case let .mention(username):
                // @
                if let flareLink = markdownLinks[username] {
                    attributes[.link] = flareLink
                } else if let url = URL(string: "https://twitter.com/\(username.dropFirst())") {
                    attributes[.link] = url
                }
                attributes[.foregroundColor] = entity.type.color(style)
            case let .hashtag(tag):
                // # 
                if let flareLink = markdownLinks[tag] {
                    attributes[.link] = flareLink
                } else if let url = URL(string: "https://twitter.com/hashtag/\(tag.dropFirst())") {
                    attributes[.link] = url
                }
                attributes[.foregroundColor] = entity.type.color(style)
            case let .cashtag(symbol):
                // $ todo:
                if let flareLink = markdownLinks[symbol] {
                    attributes[.link] = flareLink
                } else if let url = URL(string: "https://twitter.com/search?q=%24\(symbol.dropFirst())") {
                    attributes[.link] = url
                }
                attributes[.foregroundColor] = entity.type.color(style)
            case .luckyDrop, .web3:
                attributes[.foregroundColor] = entity.type.color(style)
            }

            nsAttrString.addAttributes(attributes, range: entity.range)
        }
 
        return AttributedString(nsAttrString)
    }

    private static func parseMarkdownLinks(from markdownText: String) -> [String: URL] {
        var links: [String: URL] = [:]
        // Regex     [@mention](flare://...) or [\#hashtag](flare://...)
        guard let regex = try? NSRegularExpression(pattern: "\\[(?:\\\\)?(@|#|\\$)([^\\]]+)\\]\\((flare:\\/\\/[^\\)]+)\\)", options: []) else {
            return links
        }
        let nsRange = NSRange(markdownText.startIndex ..< markdownText.endIndex, in: markdownText)
        regex.enumerateMatches(in: markdownText, options: [], range: nsRange) { match, _, _ in
            guard let match else { return }

            // Extract the entity name (e.g., @mention, #hashtag, $cashtag)
            let entityTypeRange = match.range(at: 1)
            let entityNameRange = match.range(at: 2)
            // Extract the URL
            let urlRange = match.range(at: 3)

            if let entityTypeSwiftRange = Range(entityTypeRange, in: markdownText),
               let entityNameSwiftRange = Range(entityNameRange, in: markdownText),
               let urlSwiftRange = Range(urlRange, in: markdownText)
            {
                let entityType = String(markdownText[entityTypeSwiftRange])
                let entityName = String(markdownText[entityNameSwiftRange])
                let urlString = String(markdownText[urlSwiftRange])
                if let url = URL(string: urlString) {
                    links["\(entityType)\(entityName)"] = url
                }
            }
        }
        return links
    }

    private static func parseEntities(from text: String) -> [Entity] {
        var entities: [Entity] = []

        // URLs
        if let detector: NSDataDetector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) {
            let fullRange = NSRange(location: 0, length: text.utf16.count)
            let matches = detector.matches(in: text, options: [], range: fullRange)
            for match in matches {
                if let url = match.url {
                    entities.append(Entity(type: .url(url), range: match.range))
                }
            }
        }

        // Mentions and hashtags
        let mentionPattern = "[@＠][a-zA-Z0-9_]+"
        let hashtagPattern = "[#＃][a-zA-Z0-9_]+"
        let cashtagPattern = "[$＄][a-zA-Z]+"

        for (pattern, type) in [
            (mentionPattern, { (text: String, _: NSRange) in LinkType.mention(text) }),
            (hashtagPattern, { (text: String, _: NSRange) in LinkType.hashtag(text) }),
            (cashtagPattern, { (text: String, _: NSRange) in LinkType.cashtag(text) }),
        ] {
            if let regex = try? NSRegularExpression(pattern: pattern) {
                let fullRange = NSRange(location: 0, length: text.utf16.count)
                let matches = regex.matches(in: text, range: fullRange)
                for match in matches {
                    let range = match.range
                    if let swiftRange = Range(range, in: text) {
                        let matchedText = String(text[swiftRange])
                        entities.append(Entity(type: type(matchedText, range), range: range))
                    }
                }
            }
        }

        return entities.sorted { $0.range.location < $1.range.location }
    }
 
}

 
