import Foundation
import TwitterText

public protocol TwitterTextProvider {
    func parse(text: String) -> ParseResult
    func entities(in text: String) -> [TwitterTextProviderEntity]
}

public enum TwitterTextProviderEntity {
    case url(range: NSRange)
    case screenName(range: NSRange)
    case hashtag(range: NSRange)
    case listName(range: NSRange)
    case symbol(range: NSRange)
    case tweetChar(range: NSRange)
    case tweetEmojiChar(range: NSRange)
}

public struct ParseResult {
    public let isValid: Bool
    public let weightedLength: Int
    public let maxWeightedLength: Int
    public let entities: [TwitterTextProviderEntity]

    public init(
        isValid: Bool,
        weightedLength: Int,
        maxWeightedLength: Int,
        entities: [TwitterTextProviderEntity]
    ) {
        self.isValid = isValid
        self.weightedLength = weightedLength
        self.maxWeightedLength = maxWeightedLength
        self.entities = entities
    }
}

public class SwiftTwitterTextProvider: TwitterTextProvider {
    let maxLength: Int

    public init(maxLength: Int = 280) {
        self.maxLength = maxLength
    }

    public func parse(text: String) -> ParseResult {
        ParseResult(
            isValid: text.count <= maxLength,
            weightedLength: text.count,
            maxWeightedLength: maxLength,
            entities: entities(in: text)
        )
    }

    public func entities(in text: String) -> [TwitterTextProviderEntity] {
        TwitterText.entities(in: text).compactMap { entity in
            switch entity.type {
            case .url: .url(range: entity.range)
            case .screenName: .screenName(range: entity.range)
            case .hashtag: .hashtag(range: entity.range)
            case .listname: .listName(range: entity.range)
            case .symbol: .symbol(range: entity.range)
            case .tweetChar: .tweetChar(range: entity.range)
            case .tweetEmojiChar: .tweetEmojiChar(range: entity.range)
            }
        }
    }
}
