import SwiftUI
import NaturalLanguage

struct TranslatableText: View {
    let originalText: String
    @StateObject private var viewModel = TranslationViewModel()
    private let languageDetector = LanguageDetector()
    @Environment(\.appSettings) private var appSettings
    
    private func shouldAutoTranslate() -> Bool {
        return appSettings.appearanceSettings.autoTranslate && // 检查是否开启了自动翻译
               viewModel.translatedText == nil &&             // 检查是否已经翻译过
               !viewModel.isTranslating &&                    // 检查是否正在翻译
               languageDetector.shouldTranslate(text: originalText, targetLanguage: Locale.current.language.languageCode?.identifier ?? "en")  // 检查是否需要翻译
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let translatedText = viewModel.translatedText {
                // 自定义虚线分隔线
                HStack(spacing: 2) {
                    ForEach(0..<30) { _ in
                        Rectangle()
                            .fill(Color.gray.opacity(0.3))
                            .frame(width: 4, height: 1)
                    }
                }
                .padding(.vertical, 4)
                
                Text(translatedText)
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
            if shouldAutoTranslate() {
                viewModel.translate(originalText)
            }
        }
        .onDisappear {
            // 当视图消失时重置状态，这样当它再次出现时会重新触发翻译
            viewModel.reset()
        }
    }
}

#Preview {
    TranslatableText(originalText: "你好，世界！这是一条需要翻译的测试消息。")
        .padding()
} 