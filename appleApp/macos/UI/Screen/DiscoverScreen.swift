import FlareAppleUI
import SwiftUI

struct DiscoverScreen: View {
    let onAskAi: (String?) -> Void

    init(onAskAi: @escaping (String?) -> Void = { _ in }) {
        self.onAskAi = onAskAi
    }

    var body: some View {
        DiscoverContentScreen(onAskAi: onAskAi) { _, _ in
            EmptyView()
        }
    }
}
