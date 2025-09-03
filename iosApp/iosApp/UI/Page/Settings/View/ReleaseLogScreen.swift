import MarkdownUI
import SwiftUI

struct ReleaseLogScreen: View {
    @State private var releaseLogManager = ReleaseLogManager.shared
    @State private var translationViewModel = TranslationViewModel()
    @State private var isTranslating = false
    @State private var translatedEntries: [String: String] = [:] // 按版本存储翻译结果
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if releaseLogManager.isLoading {
                    ProgressView("Loading...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ForEach(releaseLogManager.releaseLogEntries, id: \.version) { entry in
                        VStack(alignment: .leading, spacing: 12) {

                            if let translatedContent = translatedEntries[entry.version] {
                                Markdown(translatedContent)
                                    .markdownTheme(.flareMarkdownStyle(using: theme.flareTextBodyTextStyle, fontScale: theme.fontSizeScale))
                                    .padding()
                            } else {
                                Markdown(entry.content)
                                    .markdownTheme(.flareMarkdownStyle(using: theme.flareTextBodyTextStyle, fontScale: theme.fontSizeScale))
                                    .padding()
                            }
                        }
                        .background(theme.primaryBackgroundColor)
                        .cornerRadius(12)
                        .shadow(color: Color.black.opacity(0.1), radius: 2, x: 0, y: 1)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                    }
                }
            }
        }
        .background(theme.secondaryBackgroundColor)
        .navigationTitle("Release Log")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: translateContent) {
                    if isTranslating {
                        ProgressView()
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "translate")
                    }
                }
                .foregroundColor(theme.tintColor)
                .disabled(isTranslating)
            }
        }
    }

    private func translateContent() {
        guard !releaseLogManager.releaseLogEntries.isEmpty else { return }

        isTranslating = true

        Task {
            let locale = Locale.current
            let targetLanguage = locale.language.languageCode?.identifier ?? "en"
            let translationService = GoogleTranslationService(targetLanguage: targetLanguage)

            await translateWithMergedContent(translationService: translationService)
        }
    }


    private func translateWithMergedContent(translationService: GoogleTranslationService) async {
        let entries = releaseLogManager.releaseLogEntries
        let separator = "###FLARE_RELEASE_LOG_SEPARATOR###"


        let fullContent = entries.map(\.content).joined(separator: separator)

        do {

            let result = try await translationService.translate(text: fullContent)


            let translatedParts = result.translatedText.components(separatedBy: separator)


            guard translatedParts.count == entries.count else {
                print("拆分结果数量不匹配，降级到逐个翻译")
                await fallbackToIndividualTranslation(translationService: translationService)
                return
            }


            var newTranslatedEntries: [String: String] = [:]
            for (index, entry) in entries.enumerated() {
                let translatedContent = translatedParts[index].trimmingCharacters(in: .whitespacesAndNewlines)
                if !translatedContent.isEmpty {
                    newTranslatedEntries[entry.version] = translatedContent
                }
            }

            await MainActor.run {
                translatedEntries = newTranslatedEntries
                isTranslating = false
            }

        } catch {
            print("合并翻译失败，降级到逐个翻译: \(error)")
            await fallbackToIndividualTranslation(translationService: translationService)
        }
    }


    private func fallbackToIndividualTranslation(translationService: GoogleTranslationService) async {
        let entries = releaseLogManager.releaseLogEntries
        var newTranslatedEntries: [String: String] = [:]


        newTranslatedEntries = translatedEntries

        for entry in entries {

            if translatedEntries[entry.version] != nil {
                continue
            }

            do {
                let result = try await translationService.translate(text: entry.content)
                newTranslatedEntries[entry.version] = result.translatedText
            } catch {
                print("翻译条目失败 \(entry.version): \(error)")

            }
        }

        await MainActor.run {
            translatedEntries = newTranslatedEntries
            isTranslating = false
        }
    }
}
