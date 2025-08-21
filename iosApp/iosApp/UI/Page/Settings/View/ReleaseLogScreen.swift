import SwiftUI
import MarkdownUI

struct ReleaseLogScreen: View {
    @StateObject private var releaseLogManager = ReleaseLogManager.shared
    @StateObject private var translationViewModel = TranslationViewModel()
    @State private var isTranslating = false
    @State private var translatedContent: String?
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
                            if let translated = translatedContent {
                                Markdown(translated)
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
        let fullContent = releaseLogManager.releaseLogEntries
            .map { $0.content }
            .joined(separator: "\n\n----------\n\n")
        
        Task {
            do {
                let locale = Locale.current
                let targetLanguage = locale.language.languageCode?.identifier ?? "en"
                let translationService = GoogleTranslationService(targetLanguage: targetLanguage)
                let result = try await translationService.translate(text: fullContent)
                await MainActor.run {
                    self.translatedContent = result.translatedText
                    self.isTranslating = false
                }
            } catch {
                await MainActor.run {
                    self.isTranslating = false
                    print("翻译失败: \(error)")
                }
            }
        }
    }
}
