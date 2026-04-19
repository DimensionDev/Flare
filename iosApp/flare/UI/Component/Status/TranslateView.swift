import SwiftUI
import KotlinSharedUI

struct StatusTranslateView: View {
    @Environment(\.aiConfig) private var aiConfig
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
                    if content.isLongText, aiConfig.tldr {
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
                        TranslateTextView(text: cw)
                    }
                    TranslateTextView(text: content)
                }
                if enableTLDR, content.isLongText, aiConfig.tldr {
                    TLDRView(content: content, contentWarning: contentWarning)
                }
            }
        }
        
    }
}

struct TLDRView: View {
    @StateObject private var presenter: KotlinPresenter<UiState<NSString>>
    
    init(
        content: UiRichText,
        contentWarning: UiRichText?
    ) {
        let text = if let cw = contentWarning, !cw.isEmpty {
            "Content Warning:\n\(cw.toTranslatableText())\n\nContent:\n\(content.toTranslatableText())"
        } else {
            "Content:\n\(content.toTranslatableText())"
        }
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

struct TranslateTextView: View {
    @StateObject private var presenter: KotlinPresenter<UiState<UiRichText>>
    
    init(
        text: UiRichText
    ) {
        self._presenter = .init(wrappedValue: .init(presenter: TranslatePresenter(source: text, targetLanguage: Locale.current.language.languageCode?.identifier ?? "en")))
    }
    
    var body: some View {
        StateView(state: presenter.state) { text in
            RichText(text: text)
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
