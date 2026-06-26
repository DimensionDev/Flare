import KotlinSharedUI
import SwiftUI
import FlareAppleCore

public struct TranslateStatusComponent: View {
    private let data: TranslationDisplayState

    public init(data: TranslationDisplayState) {
        self.data = data
    }

    public var body: some View {
        HStack {
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
    }
}
