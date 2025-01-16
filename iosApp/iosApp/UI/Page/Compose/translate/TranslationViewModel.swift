import Foundation
import SwiftUI

@MainActor
class TranslationViewModel: ObservableObject {
    @Published var translatedText: String?
    @Published var isTranslating = false
    @Published var error: Error?

    private let translationService: TranslationService

    init() {
        let locale = Locale.current
        let targetLanguage = locale.language.languageCode?.identifier ?? "en"
        translationService = GoogleTranslationService(targetLanguage: targetLanguage)
        // self.translationService = AppleTranslationServiceImpl(targetLanguage: targetLanguage)
    }

    func translate(_ text: String) {
        guard !isTranslating else { return }

        isTranslating = true
        error = nil

        Task {
            do {
                let result = try await translationService.translate(text: text)
                translatedText = result.translatedText
            } catch {
                self.error = error
            }
            isTranslating = false
        }
    }

    func reset() {
        translatedText = nil
        error = nil
        isTranslating = false
    }
}
