import SwiftUI


struct FloatingScrollToTopButton: View {

    @Binding var isVisible: Bool


    @Binding var scrollToTopTrigger: Bool

    @Environment(FlareTheme.self) private var theme

    var body: some View {
        Group {
            if isVisible {
                Button(action: scrollToTopAction) {
                    buttonContent
                }
                .transition(.scale.combined(with: .opacity))
                .animation(.spring(response: FloatingButtonConfig.springResponse,
                                   dampingFraction: FloatingButtonConfig.springDamping),
                           value: isVisible)
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
        scrollToTopTrigger.toggle()
    }
}
