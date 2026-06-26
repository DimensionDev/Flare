import FlareAppleUI
import KotlinSharedUI
import SwiftUI

struct LocalHistoryScreen: View {
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled

    let onAskAi: (String?, String) -> Void

    init(onAskAi: @escaping (String?, String) -> Void = { _, _ in }) {
        self.onAskAi = onAskAi
    }

    var body: some View {
        LocalHistoryContentScreen(onAskAi: onAskAi) { isSearchPresented, askAi in
            if agentEnabled && isSearchPresented {
                AskAiSearchAccessory(action: askAi)
                    .padding(.bottom, 0)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .zIndex(1)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: agentEnabled)
    }
}
