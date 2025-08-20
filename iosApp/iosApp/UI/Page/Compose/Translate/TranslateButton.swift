import SwiftUI

struct TranslateButton: View {
    let text: String
    let onTranslate: () -> Void
    let isTranslating: Bool
    let isTranslated: Bool

    var body: some View {
        Button(action: {
            FlareHapticManager.shared.buttonPress()
            onTranslate()
        }) {
            HStack(spacing: 4) {
                if isTranslating {
                    ProgressView()
                        .scaleEffect(0.8)
                } else {
                    Image(systemName: isTranslated ? "globe.americas.fill" : "globe")
                }
                Text(isTranslated ? "translated" : "translate")
                    .font(.footnote)
            }
            .foregroundColor(.accentColor)
        }
        .disabled(isTranslating)
        .allowsHitTesting(true)
        .contentShape(Rectangle())
        .onTapGesture {
            FlareHapticManager.shared.buttonPress()
            onTranslate()
        }
    }
}
