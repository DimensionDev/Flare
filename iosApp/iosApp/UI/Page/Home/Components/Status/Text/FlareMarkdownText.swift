import Foundation
import TwitterText
import UIKit
import SwiftUI

public enum FlareMarkdownText {
    // MARK: - Constants
    public static let lensIDRegex: NSRegularExpression? = {
        guard let regex = try? NSRegularExpression(
            pattern: "[@|#|/][a-zA-Z0-9-_./]+",
            options: []
        ) else {
            print("Failed to create regex")
            return nil
        }
        return regex
    }()
    
    // MARK: - Style Configuration
    public struct Style {
        public let font: UIFont
        public let textColor: UIColor
        public let linkColor: UIColor
        public let mentionColor: UIColor
        public let hashtagColor: UIColor
        public let cashtagColor: UIColor
        
        public static let `default` = Style(
            font: .systemFont(ofSize: 16),
            textColor: Colors.Text.primary,
            linkColor: Colors.Link.hyperlink,
            mentionColor: Colors.Link.mention,
            hashtagColor: Colors.Link.hashtag,
            cashtagColor: Colors.Link.cashtag
        )
        
        public static let timeline = Style(
            font: .systemFont(ofSize: 16),
            textColor: Colors.Text.primary,
            linkColor: Colors.Link.hyperlink,
            mentionColor: Colors.Link.mention,
            hashtagColor: Colors.Link.hashtag,
            cashtagColor: Colors.Link.cashtag
        )
        
        public static let quote = Style(
            font: .systemFont(ofSize: 15),
            textColor: Colors.Text.secondary,
            linkColor: Colors.Link.hyperlink.withAlphaComponent(0.8),
            mentionColor: Colors.Link.mention.withAlphaComponent(0.8),
            hashtagColor: Colors.Link.hashtag.withAlphaComponent(0.8),
            cashtagColor: Colors.Link.cashtag.withAlphaComponent(0.8)
        )
        
        public init(
            font: UIFont = .systemFont(ofSize: 16),
            textColor: UIColor = Colors.Text.primary,
            linkColor: UIColor = Colors.Link.hyperlink,
            mentionColor: UIColor = Colors.Link.mention,
            hashtagColor: UIColor = Colors.Link.hashtag,
            cashtagColor: UIColor = Colors.Link.cashtag
        ) {
            self.font = font
            self.textColor = textColor
            self.linkColor = linkColor
            self.mentionColor = mentionColor
            self.hashtagColor = hashtagColor
            self.cashtagColor = cashtagColor
        }
    }
    
    // MARK: - Link Types
    public enum LinkType {
        case url(URL)
        case mention(String)
        case hashtag(String)
        case cashtag(String)
        case luckyDrop
        case web3
        
        var color: (Style) -> UIColor {
            switch self {
            case .url: return { $0.linkColor }
            case .mention: return { $0.mentionColor }
            case .hashtag: return { $0.hashtagColor }
            case .cashtag: return { $0.cashtagColor }
            case .luckyDrop: return { $0.linkColor }
            case .web3: return { $0.linkColor }
            }
        }
    }

    /// parse and combinate attributeString in markdown style
    public static func attributeString(
        of originalText: String?,
        prependLinks: [String: URL] = [:],
        embeds: [String] = [],
        style: Style = .default,
        previewLinkValidator: @escaping (String) -> Bool = { _ in false }
    ) -> (
        markdown: AttributedString,
        previewLink: String?,
        luckyDropLink: String?
    ) {
        let withOutMediaUrls = embeds.filter({ !$0.isImageLink && !$0.isVideoLink })
        
        guard let originalText, !originalText.isEmpty else {
            return (AttributedString(), withOutMediaUrls.last, nil)
        }
        
        let text = originalText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .squashingNewlines()
            .replacingOccurrences(of: "~~", with: " ~~ ")

        var attributedString = {
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
        
        // Apply default style
        nsAttrString.addAttributes(
            [
                .font: style.font,
                .foregroundColor: style.textColor
            ],
            range: NSRange(location: 0, length: text.count)
        )
        
        // Process different types of links
        let entities = parseEntities(from: text)
        for entity in entities {
            var attributes: [NSAttributedString.Key: Any] = [
                .font: style.font
            ]
            
            switch entity.type {
            case .url(let url):
                attributes[.link] = url
                attributes[.foregroundColor] = entity.type.color(style)
            case .mention(let username):
                if let url = URL(string: "https://twitter.com/\(username.dropFirst())") {
                    attributes[.link] = url
                }
                attributes[.foregroundColor] = entity.type.color(style)
            case .hashtag(let tag):
                if let url = URL(string: "https://twitter.com/hashtag/\(tag.dropFirst())") {
                    attributes[.link] = url
                }
                attributes[.foregroundColor] = entity.type.color(style)
            case .cashtag(let symbol):
                if let url = URL(string: "https://twitter.com/search?q=%24\(symbol.dropFirst())") {
                    attributes[.link] = url
                }
                attributes[.foregroundColor] = entity.type.color(style)
            case .luckyDrop, .web3:
                attributes[.foregroundColor] = entity.type.color(style)
            }
            
            nsAttrString.addAttributes(attributes, range: entity.range)
        }
        
        // Handle preview and lucky drop links
        let (previewLink, luckyDropLink) = processSpecialLinks(
            entities: entities,
            withOutMediaUrls: withOutMediaUrls,
            previewLinkValidator: previewLinkValidator
        )
        
        return (AttributedString(nsAttrString), previewLink, luckyDropLink)
    }
    
    // MARK: - Private Methods
    private static func parseEntities(from text: String) -> [Entity] {
        var entities: [Entity] = []
        
        // URLs
        if let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) {
            let matches = detector.matches(in: text, options: [], range: NSRange(location: 0, length: text.utf16.count))
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
            (mentionPattern, { (text: String, range: NSRange) in LinkType.mention(text) }),
            (hashtagPattern, { (text: String, range: NSRange) in LinkType.hashtag(text) }),
            (cashtagPattern, { (text: String, range: NSRange) in LinkType.cashtag(text) })
        ] {
            if let regex = try? NSRegularExpression(pattern: pattern) {
                let matches = regex.matches(in: text, range: NSRange(text.startIndex..., in: text))
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
    
    private static func processSpecialLinks(
        entities: [Entity],
        withOutMediaUrls: [String],
        previewLinkValidator: (String) -> Bool
    ) -> (previewLink: String?, luckyDropLink: String?) {
        var previewLink: String?
        var luckyDropLink: String?
        
        // Process entities for lucky drop links
        for entity in entities {
            if case .url(let url) = entity.type {
                let urlString = url.absoluteString
                if urlString.isLuckDropLink {
                    luckyDropLink = urlString
                    break
                }
            }
        }
        
        // Process preview links if no lucky drop link found
        if luckyDropLink == nil {
            if entities.isEmpty {
                previewLink = withOutMediaUrls.last
            } else {
                for entity in entities.reversed() {
                    if case .url(let url) = entity.type {
                        let urlString = url.absoluteString
                        if previewLinkValidator(urlString) {
                            previewLink = urlString
                            break
                        }
                    }
                }
            }
        }
        
        return (previewLink, luckyDropLink)
    }
}

// MARK: - Supporting Types
extension FlareMarkdownText {
    struct Entity {
        let type: LinkType
        let range: NSRange
    }
}

// MARK: - String Extensions
private extension String {
    var isImageLink: Bool {
        let imageExtensions = [".jpg", ".jpeg", ".png", ".gif", ".webp"]
        return imageExtensions.contains { self.lowercased().hasSuffix($0) }
    }
    
    var isVideoLink: Bool {
        let videoExtensions = [".mp4", ".mov", ".avi", ".webm"]
        return videoExtensions.contains { self.lowercased().hasSuffix($0) }
    }
    
    var isLuckDropLink: Bool {
        // Empty implementation as requested
        return false
    }
    
    func squashingNewlines() -> String {
        return self.replacingOccurrences(
            of: "\\n\\s*\\n",
            with: "\n",
            options: .regularExpression
        )
    }
}
