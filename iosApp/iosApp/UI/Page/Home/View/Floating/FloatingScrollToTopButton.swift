import SwiftUI

struct FloatingScrollToTopButton: View {
    @EnvironmentObject private var timelineState: TimelineExtState
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        Group {
            if timelineState.showFloatingButton {
                Button(action: scrollToTopAction) {
                    buttonContent
                }
                .transition(.scale.combined(with: .opacity))
                .animation(.spring(response: FloatingButtonConfig.springResponse,
                                   dampingFraction: FloatingButtonConfig.springDamping),
                           value: timelineState.showFloatingButton)
            }
        }
    }

    private var buttonContent: some View {
        Image(systemName: "arrow.up")
            .font(.system(size: FloatingButtonConfig.iconSize, weight: .medium))
            .foregroundColor(.white)
            .frame(width: FloatingButtonConfig.buttonSize, height: FloatingButtonConfig.buttonSize)
            .background(
                Circle()
                    .fill(theme.tintColor)
                    .shadow(
                        color: .black.opacity(FloatingButtonConfig.shadowOpacity),
                        radius: FloatingButtonConfig.shadowRadius,
                        x: FloatingButtonConfig.shadowOffset.width,
                        y: FloatingButtonConfig.shadowOffset.height
                    )
            )
    }

    private func scrollToTopAction() {
        timelineState.scrollToTopTrigger.toggle()
    }
}
