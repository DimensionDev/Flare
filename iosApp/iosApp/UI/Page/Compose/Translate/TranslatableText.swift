import NaturalLanguage
import SwiftUI
#if canImport(_Translation_SwiftUI)
    import Translation
#endif

struct TranslatableText: View {
    let originalText: String
    let forceTranslate: Bool
    @StateObject private var viewModel = TranslationViewModel()
    private let languageDetector = LanguageDetector()
    @Environment(\.appSettings) private var appSettings
    @Environment(\.isInCaptureMode) private var isInCaptureMode // 截图不翻译


    init(originalText: String) {
        self.originalText = originalText
        self.forceTranslate = false
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
            languageDetector.shouldTranslate(text: originalText, targetLanguage: Locale.current.language.languageCode?.identifier ?? "en") // 检查是否需要翻译
    }


    private func getTranslatedText() -> String? {
        viewModel.translatedText
    }


    private func isTranslating() -> Bool {
        viewModel.isTranslating
    }


    private func startTranslation() {
        viewModel.translate(originalText)
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

            if let error = viewModel.error {
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

