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
        return ParseResult(
            isValid: text.count <= maxLength,
            weightedLength: text.count,
            maxWeightedLength: maxLength,
            entities: self.entities(in: text)
        )
    }
    
    public func entities(in text: String) -> [TwitterTextProviderEntity] {
        return TwitterText.entities(in: text).compactMap { entity in
            switch entity.type {
            case .url:              return .url(range: entity.range)
            case .screenName:       return .screenName(range: entity.range)
            case .hashtag:          return .hashtag(range: entity.range)
            case .listname:         return .listName(range: entity.range)
            case .symbol:           return .symbol(range: entity.range)
            case .tweetChar:        return .tweetChar(range: entity.range)
            case .tweetEmojiChar:   return .tweetEmojiChar(range: entity.range)
            }
        }
    }
}
