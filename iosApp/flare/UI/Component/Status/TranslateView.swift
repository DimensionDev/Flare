import SwiftUI
import KotlinSharedUI

struct StatusTranslateView: View {
    @Environment(\.themeSettings) private var themeSettings
    let content: UiRichText
    let contentWarning: UiRichText?
    @State private var enableTranslate: Bool = false
    @State private var enableTLDR: Bool = false

    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            if !content.isEmpty {
                HStack {
                    Button {
                        enableTranslate.toggle()
                    } label: {
                        Text("status_translate")
                    }
                    .buttonStyle(.borderless)
                    if content.isLongText, themeSettings.aiConfig.tldr {
                        Button {
                            enableTLDR.toggle()
                        } label: {
                            Text("status_tldr")
                        }
                        .buttonStyle(.borderless)
                    }
                }
                
                if enableTranslate {
                    if let cw = contentWarning {
                        TranslateTextView(text: cw.innerText, useAI: themeSettings.aiConfig.translation)
                    }
                    TranslateTextView(text: content.innerText, useAI: themeSettings.aiConfig.translation)
                }
                if enableTLDR, content.isLongText, themeSettings.aiConfig.tldr {
                    
                }
            }
        }
        
    }
}

struct TranslateTextView: View {
    @StateObject private var presenter: KotlinPresenter<UiState<NSString>>
    
    init(
        text: String,
        useAI: Bool = false
    ) {
        if useAI {
            self._presenter = .init(wrappedValue: .init(presenter: AiTranslatePresenter(source: text, targetLanguage: Locale.current.language.languageCode?.identifier ?? "en")))
        } else {
            self._presenter = .init(wrappedValue: .init(presenter: TranslatePresenter(source: text, targetLanguage: Locale.current.language.languageCode?.identifier ?? "en")))
        }
    }
    
    var body: some View {
        StateView(state: presenter.state) { text in
            Text(String(text))
        } errorContent: { error in
            Text(error.message ?? "Unknown Error")
        } loadingContent: {
            ProgressView()
        }
    }
}


struct TLDRTextView: View {
    @StateObject private var presenter: KotlinPresenter<UiState<NSString>>
    
    init(
        text: String
    ) {
        self._presenter = .init(wrappedValue: .init(presenter: AiTLDRPresenter(source: text, targetLanguage: Locale.current.language.languageCode?.identifier ?? "en")))
    }
    
    var body: some View {
        StateView(state: presenter.state) { text in
            Text(String(text))
        } errorContent: { error in
            Text(error.message ?? "Unknown Error")
        } loadingContent: {
            ProgressView()
        }
    }
}
