import Foundation
import NaturalLanguage

public enum TranslationProvider: String, Codable {
    case systemOffline
    case google
}

public protocol TranslationService {
    func translate(text: String) async throws -> TranslationResult
}

public class TranslationResult: NSObject {
    public let translatedText: String
    public let sourceLanguage: String?
    public let targetLanguage: String
    public let provider: TranslationProvider

    public init(translatedText: String, sourceLanguage: String?, targetLanguage: String, provider: TranslationProvider) {
        self.translatedText = translatedText
        self.sourceLanguage = sourceLanguage
        self.targetLanguage = targetLanguage
        self.provider = provider
        super.init()
    }
}

public enum TranslationError: LocalizedError {
    case serviceUnavailable
    case languageNotSupported(String)
    case networkError(Error)
    case translationFailed
    case permissionDenied
    case versionNotSupported

    public var errorDescription: String? {
        switch self {
        case .serviceUnavailable:
            "Translation service is not available on this device"
        case let .languageNotSupported(language):
            "Language '\(language)' is not supported"
        case let .networkError(error):
            "Network error: \(error.localizedDescription)"
        case .translationFailed:
            "Translation failed"
        case .permissionDenied:
            "Translation permission denied"
        case .versionNotSupported:
            "Translation requires iOS 15.0 or later"
        }
    }
}
