import SwiftUI

struct TranslateButton: View {
    let text: String
    let onTranslate: () -> Void
    let isTranslating: Bool
    let isTranslated: Bool
    
    var body: some View {
        Button(action: onTranslate) {
            HStack(spacing: 4) {
                if isTranslating {
                    ProgressView()
                        .scaleEffect(0.8)
                } else {
                    Image(systemName: isTranslated ? "globe.americas.fill" : "globe")
                }
                Text(isTranslated ? "已翻译" : "翻译")
                    .font(.footnote)
            }
            .foregroundColor(.accentColor)
        }
        .disabled(isTranslating)
        .allowsHitTesting(true)
        .contentShape(Rectangle())
        .onTapGesture {
            onTranslate()
        }
    }
}

#Preview {
    VStack {
        TranslateButton(
            text: "Hello World",
            onTranslate: {},
            isTranslating: false,
            isTranslated: false
        )
        
        TranslateButton(
            text: "Hello World",
            onTranslate: {},
            isTranslating: true,
            isTranslated: false
        )
        
        TranslateButton(
            text: "Hello World",
            onTranslate: {},
            isTranslating: false,
            isTranslated: true
        )
    }
} 