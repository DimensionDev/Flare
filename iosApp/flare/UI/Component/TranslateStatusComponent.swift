import KotlinSharedUI
import SwiftUI

struct TranslateStatusComponent: View {
    let data: TranslationDisplayState
    
    var body: some View {
        HStack {
            Image(.faLanguage)
            switch data {
            case .failed: Image(.faCircleExclamation)
            case .translating: ProgressView().progressViewStyle(.circular).scaledToFit().frame(width: 12, height: 12)
            default: EmptyView()
            }
        }
    }
}
