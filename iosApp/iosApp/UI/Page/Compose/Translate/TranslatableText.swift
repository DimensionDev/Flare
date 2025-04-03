import NaturalLanguage
import SwiftUI
#if canImport(_Translation_SwiftUI)
import Translation
#endif

struct TranslatableText: View {

    let originalText: String
    @StateObject private var viewModel = TranslationViewModel()
    private let languageDetector = LanguageDetector()
    @Environment(\.appSettings) private var appSettings
    @Environment(\.isInCaptureMode) private var isInCaptureMode // 截图不翻译
    
    // 添加Apple翻译相关的状态
    @State private var translationConfig: TranslationSession.Configuration?
    @State private var isAppleTranslating = false
    @State private var appleTranslatedText: String?
    @State private var translationTask: Task<Void, Never>?
    
    private func shouldAutoTranslate() -> Bool {
        
        if isInCaptureMode {
            return false
        }
        
        return appSettings.appearanceSettings.autoTranslate && // 检查是否开启了自动翻译
        getTranslatedText() == nil && // 检查是否已经翻译过
        !isTranslating() && // 检查是否正在翻译
        languageDetector.shouldTranslate(text: originalText, targetLanguage: Locale.current.language.languageCode?.identifier ?? "en") // 检查是否需要翻译
    }
    
    // 统一获取翻译文本
    private func getTranslatedText() -> String? {
        if appSettings.otherSettings.translationProvider == .systemOffline {
            return appleTranslatedText
        } else {
            return viewModel.translatedText
        }
    }
    
    // 统一获取翻译状态
    private func isTranslating() -> Bool {
        if appSettings.otherSettings.translationProvider == .systemOffline {
            return isAppleTranslating
        } else {
            return viewModel.isTranslating
        }
    }
    
    // 开始翻译
    private func startTranslation() {
        if appSettings.otherSettings.translationProvider == .systemOffline {
            if #available(iOS 18, *) {
                if translationConfig == nil {
                    translationConfig = TranslationSession.Configuration()
                    // 设置target
                    let locale = Locale.current
                    if let languageCode = locale.language.languageCode?.identifier {
                        translationConfig?.target = Locale.Language(identifier: languageCode)
                    }
                } else {
                    translationConfig?.invalidate()
                }
                isAppleTranslating = true
            }
        } else {
            viewModel.translate(originalText)
        }
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let translatedText = getTranslatedText() {
                
                HStack(spacing: 2) {
                    ForEach(0..<30) { _ in
                        Rectangle()
                            .fill(Color.gray.opacity(0.3))
                            .frame(width: 4, height: 1)
                    }
                }
                .padding(.vertical, 4)
                
                Text(translatedText + "\n -- \(appSettings.otherSettings.translationProvider == .systemOffline ? "System Offline" : "Google Translate")")
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
          
            if !isInCaptureMode && shouldAutoTranslate() {
                startTranslation()
            }
        }
        .onDisappear {
            // 取消正在进行的翻译任务
            translationTask?.cancel()
            translationTask = nil
            // 当视图消失时重置状态，这样当它再次出现时会重新触发翻译
            viewModel.reset()
            appleTranslatedText = nil
            isAppleTranslating = false
        } 
        .modifier(OfflineTranslationModifier(
            isEnabled: !isInCaptureMode && appSettings.otherSettings.translationProvider == .systemOffline,
            config: translationConfig,
            text: originalText,
            isTranslating: $isAppleTranslating,
            translatedText: $appleTranslatedText,
            translationTask: $translationTask
        ))
    }
}

 
@available(iOS 18, *)
private struct OfflineTranslationModifier: ViewModifier {
    let isEnabled: Bool
    let config: TranslationSession.Configuration?
    let text: String
    @Binding var isTranslating: Bool
    @Binding var translatedText: String?
    @Binding var translationTask: Task<Void, Never>?
    
    func body(content: Content) -> some View {
        if isEnabled {
            content.translationTask(config) { session in
                
                translationTask?.cancel()
                translationTask = Task {
                    do {
                        
                        try Task.checkCancellation()
                        let response = try await session.translate(text)
                        if !Task.isCancelled {
                            translatedText = response.targetText
                        }
                    } catch {
                        if !Task.isCancelled {
                            print("Translation error: \(error)")
                        }
                    }
                    if !Task.isCancelled {
                        isTranslating = false
                    }
                }
            }
        } else {
            content
        }
    }
}
