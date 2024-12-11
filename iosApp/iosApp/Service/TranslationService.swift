import Foundation
import NaturalLanguage

public protocol TranslationService {
    func translate(text: String) async throws -> TranslationResult
}

public struct TranslationResult {
    public let translatedText: String
    public let sourceLanguage: String?
    public let targetLanguage: String
    
    public init(translatedText: String, sourceLanguage: String?, targetLanguage: String) {
        self.translatedText = translatedText
        self.sourceLanguage = sourceLanguage
        self.targetLanguage = targetLanguage
    }
}

public class AppleTranslationService: TranslationService {
    private let translator = NLTranslator()
    private let targetLanguage: String
    
    public init(targetLanguage: String = "en") {
        self.targetLanguage = targetLanguage
    }
    
    public func translate(text: String) async throws -> TranslationResult {
        // Set target language
        try await translator.setTargetLanguage(NLLanguage(targetLanguage))
        
        // Detect source language
        let sourceLanguage = NLLanguageRecognizer.dominantLanguage(for: text)?.rawValue
        
        // Translate text
        let translatedText = try await translator.translate(text)
        
        return TranslationResult(
            translatedText: translatedText,
            sourceLanguage: sourceLanguage,
            targetLanguage: targetLanguage
        )
    }
}
