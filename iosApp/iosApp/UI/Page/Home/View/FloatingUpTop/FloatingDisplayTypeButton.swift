import SwiftUI


struct FloatingDisplayTypeButton: View {
     @Binding var isVisible: Bool
    
     @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme
    
     private var currentDisplayType: TimelineDisplayType {
        appSettings.appearanceSettings.timelineDisplayType
    }
    
     private var nextDisplayType: TimelineDisplayType {
        switch currentDisplayType {
        case .timeline:
            return .mediaWaterfall
        case .mediaWaterfall:
            return .mediaCardWaterfall
        case .mediaCardWaterfall:
            return .timeline
        }
    }
    
    var body: some View {
        Group {
            if isVisible {
                Button(action: switchDisplayType) {
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
        Image(systemName: currentDisplayType.systemImage)
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
    
     private func switchDisplayType() {
        FlareLog.debug("FloatingDisplayTypeButton 切换显示模式: \(currentDisplayType.displayName) -> \(nextDisplayType.displayName)")
        
         let newSettings = appSettings.appearanceSettings.changing(
            path: \.timelineDisplayType,
            to: nextDisplayType
        )
        appSettings.update(newValue: newSettings)
        
         let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
        
        FlareLog.debug("FloatingDisplayTypeButton 显示模式已切换到: \(nextDisplayType.displayName)")
    }
}

