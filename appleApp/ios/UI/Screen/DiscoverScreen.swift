import FlareAppleUI
@preconcurrency import KotlinSharedUI
import SwiftUI

struct DiscoverScreen: View {
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled

    let onAskAi: (String?) -> Void

    init(onAskAi: @escaping (String?) -> Void = { _ in }) {
        self.onAskAi = onAskAi
    }

    var body: some View {
        DiscoverContentScreen(onAskAi: onAskAi) { isSearchPresented, askAi in
            if agentEnabled && isSearchPresented {
                AskAiSearchAccessory(action: askAi)
                    .padding(.bottom, 16)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .zIndex(1)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: agentEnabled)
    }
}
