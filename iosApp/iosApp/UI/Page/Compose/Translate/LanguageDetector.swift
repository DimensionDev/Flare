import Foundation
import NaturalLanguage

/// Language detection helper
public class LanguageDetector {
    private let recognizer = NLLanguageRecognizer()

    public init() {}

    public func detectLanguage(for text: String) -> String {
        recognizer.processString(text)
        let language = recognizer.dominantLanguage?.rawValue ?? "auto"
        recognizer.reset()
        return language
    }

    private func normalizeLanguageCode(_ code: String) -> String {
        let code = code.lowercased()
        if code.starts(with: "zh") || code == "cn" {
            return "zh"
        }
        return code.split(separator: "-").first.map(String.init) ?? code
    }

    public func shouldTranslate(text: String, targetLanguage: String) -> Bool {
        let detectedLanguage = normalizeLanguageCode(detectLanguage(for: text))
        let target = normalizeLanguageCode(targetLanguage)

        return detectedLanguage != "auto" && detectedLanguage != target
    }
}
