
import CoreHaptics
import SwiftUI
import Foundation
#if canImport(UIKit)
import UIKit
#endif

 @MainActor
@Observable
public final class FlareHapticManager {
    
     public static let shared = FlareHapticManager()
    

     public enum ImpactStyle: String, CaseIterable {
        case light = "light"
        case medium = "medium"
        case heavy = "heavy"
        case soft = "soft"
        case rigid = "rigid"

        public var displayName: String {
            switch self {
            case .light: return "Light"
            case .medium: return "Medium"
            case .heavy: return "Heavy"
            case .soft: return "Soft"
            case .rigid: return "Rigid"
            }
        }
    }

     public enum NotificationStyle: String, CaseIterable {
        case success = "success"
        case warning = "warning"
        case error = "error"

        public var displayName: String {
            switch self {
            case .success: return "Success"
            case .warning: return "Warning"
            case .error: return "Error"
            }
        }
    }

     public enum HapticType {
        case impact(ImpactStyle)
        case selection
        case notification(NotificationStyle)
        case buttonPress
        case tabSelection
        case dataRefresh(intensity: CGFloat = 1.0)

        // Convenience access
        public static let light = HapticType.impact(.light)
        public static let medium = HapticType.impact(.medium)
        public static let heavy = HapticType.impact(.heavy)
        public static let soft = HapticType.impact(.soft)
        public static let rigid = HapticType.impact(.rigid)
        public static let success = HapticType.notification(.success)
        public static let warning = HapticType.notification(.warning)
        public static let error = HapticType.notification(.error)
    }
    

    #if !os(visionOS) && canImport(UIKit)
    private var impactGenerators: [UIImpactFeedbackGenerator.FeedbackStyle: UIImpactFeedbackGenerator] = [:]
    private let selectionGenerator = UISelectionFeedbackGenerator()
    private let notificationGenerator = UINotificationFeedbackGenerator()
    #endif

     private var appSettings: AppSettings {
        return AppSettings()
    }
    

    private init() {
        #if !os(visionOS) && canImport(UIKit)
         selectionGenerator.prepare()
        notificationGenerator.prepare()
        #endif
    }
    


     public var supportsHaptics: Bool {
        #if os(visionOS)
        return false
        #elseif canImport(UIKit)
        return CHHapticEngine.capabilitiesForHardware().supportsHaptics
        #else
        return false
        #endif
    }

     public func prepare(_ type: HapticType) {
        #if !os(visionOS) && canImport(UIKit)
        guard supportsHaptics else { return }

        switch type {
        case .impact(let style):
            impactGenerator(for: uiImpactStyle(from: style)).prepare()
        case .selection, .tabSelection:
            selectionGenerator.prepare()
        case .notification, .buttonPress:
            notificationGenerator.prepare()
        case .dataRefresh:
            impactGenerator(for: .heavy).prepare()
        }
        #endif
    }
    
     public func generate(_ type: HapticType) {
        #if !os(visionOS) && canImport(UIKit)
        guard supportsHaptics else { return }

        
        guard shouldGenerateHaptic(for: type) else { return }

        switch type {
        case .impact(let style):
            impactGenerator(for: uiImpactStyle(from: style)).impactOccurred()

        case .selection:
            selectionGenerator.selectionChanged()

        case .notification(let style):
            notificationGenerator.notificationOccurred(uiNotificationStyle(from: style))

        case .buttonPress:
            let userIntensity = getUserIntensityStyle()
            impactGenerator(for: userIntensity).impactOccurred()

        case .tabSelection:
            selectionGenerator.selectionChanged()

        case .dataRefresh(let intensity):
            let userIntensity = getUserIntensityStyle()
            impactGenerator(for: userIntensity).impactOccurred(intensity: intensity)
        }
        #endif
    }
    

     public func buttonPress() {
        generate(.buttonPress)
    }

     public func tabSelection() {
        generate(.tabSelection)
    }

     public func selection() {
        generate(.selection)
    }

     public func success() {
        generate(.success)
    }

     public func warning() {
        generate(.warning)
    }

     public func error() {
        generate(.error)
    }

     public func dataRefresh(intensity: CGFloat = 1.0) {
        generate(.dataRefresh(intensity: intensity))
    }
}


extension FlareHapticManager {
    
    #if !os(visionOS) && canImport(UIKit)
     private func impactGenerator(for style: UIImpactFeedbackGenerator.FeedbackStyle) -> UIImpactFeedbackGenerator {
        if let generator = impactGenerators[style] {
            return generator
        } else {
            let generator = UIImpactFeedbackGenerator(style: style)
            impactGenerators[style] = generator
            return generator
        }
    }

  
    private func uiImpactStyle(from style: ImpactStyle) -> UIImpactFeedbackGenerator.FeedbackStyle {
        switch style {
        case .light: return .light
        case .medium: return .medium
        case .heavy: return .heavy
        case .soft: return .soft
        case .rigid: return .rigid
        }
    }

     private func uiNotificationStyle(from style: NotificationStyle) -> UINotificationFeedbackGenerator.FeedbackType {
        switch style {
        case .success: return .success
        case .warning: return .warning
        case .error: return .error
        }
    }
    #endif

     private func shouldGenerateHaptic(for type: HapticType) -> Bool {
        return appSettings.appearanceSettings.hapticFeedback.isEnabled
    }

    #if !os(visionOS) && canImport(UIKit)
     private func getUserIntensityStyle() -> UIImpactFeedbackGenerator.FeedbackStyle {
        let userIntensity = appSettings.appearanceSettings.hapticFeedback.intensity
        switch userIntensity {
        case .light: return .light
        case .medium: return .medium
        case .heavy: return .heavy
        }
    }
    #endif
}


extension FlareHapticManager {
    
     public struct HapticFeedbackModifier: ViewModifier {
        let hapticType: HapticType

        public func body(content: Content) -> some View {
            content
                .onTapGesture {
                    FlareHapticManager.shared.generate(hapticType)
                }
        }
    }
}

 
extension View {

 
    public func hapticFeedback(_ type: FlareHapticManager.HapticType) -> some View {
        modifier(FlareHapticManager.HapticFeedbackModifier(hapticType: type))
    }

     
    public func hapticButtonPress() -> some View {
        hapticFeedback(.buttonPress)
    }
}

