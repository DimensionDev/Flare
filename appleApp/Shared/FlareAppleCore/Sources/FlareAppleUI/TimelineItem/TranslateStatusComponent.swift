import KotlinSharedUI
import SwiftUI
import FlareAppleCore

public struct TranslateStatusComponent: View {
    private let data: TranslationDisplayState

    public init(data: TranslationDisplayState) {
        self.data = data
    }

    public var body: some View {
        HStack(spacing: 4) {
            Image(fontAwesome: .language)
            switch data {
            case .failed: Image(fontAwesome: .circleExclamation)
            case .translating: ProgressView()
                    .progressViewStyle(.circular)
                #if os(macOS)
                    .scaleEffect(0.5)
                #endif
                    .scaledToFit()
                    .frame(width: 12, height: 12)
            default: EmptyView()
            }
        }
        .accessibilityLabel(Text(verbatim: accessibilityLabel))
    }

    private var accessibilityLabel: String {
        switch data {
        case .failed:
            String(localized: "translation_failed", defaultValue: "Translation failed")
        case .translating:
            String(localized: "translation_in_progress", defaultValue: "Translation in progress")
        default:
            String(localized: "translation_available", defaultValue: "Translation available")
        }
    }
}
