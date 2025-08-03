import NaturalLanguage
import SwiftUI
#if canImport(_Translation_SwiftUI)
    import Translation
#endif

struct TranslatableText: View {
    let originalText: String
    let forceTranslate: Bool
    @StateObject private var transViewModel = TranslationViewModel()
    private let languageDetector = LanguageDetector()
    @Environment(\.appSettings) private var appSettings
    @Environment(\.isInCaptureMode) private var isInCaptureMode

    init(originalText: String) {
        self.originalText = originalText
        forceTranslate = false
    }

    init(originalText: String, forceTranslate: Bool) {
        self.originalText = originalText
        self.forceTranslate = forceTranslate
    }

    private func shouldTranslate() -> Bool {
        if isInCaptureMode {
            return false
        }

        return forceTranslate &&
            getTranslatedText() == nil &&
            !isTranslating() &&
            languageDetector.shouldTranslate(text: originalText, targetLanguage: Locale.current.language.languageCode?.identifier ?? "en")
    }

    private func getTranslatedText() -> String? {
        transViewModel.translatedText
    }

    private func isTranslating() -> Bool {
        transViewModel.isTranslating
    }

    private func startTranslation() {
        transViewModel.translate(originalText)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let translatedText = getTranslatedText() {
                HStack(spacing: 2) {
                    ForEach(0 ..< 30) { _ in
                        Rectangle()
                            .fill(Color.gray.opacity(0.3))
                            .frame(width: 4, height: 1)
                    }
                }
                .padding(.vertical, 4)

                Text(translatedText + "\n -- Google Translate")
                    .font(.body)
                    .foregroundColor(.secondary)
            }

            if let error = transViewModel.error {
                Text(error.localizedDescription)
                    .font(.caption)
                    .foregroundColor(.red)
            }
        }
        .onAppear {
            if shouldTranslate() {
                startTranslation()
            }
        }
    }
}
