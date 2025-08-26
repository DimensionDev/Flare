
import CoreHaptics
import Foundation
import SwiftUI
#if canImport(UIKit)
    import UIKit
#endif

@MainActor
@Observable
public final class FlareHapticManager {
    public static let shared = FlareHapticManager()

    public enum ImpactStyle: String, CaseIterable {
        case light
        case medium
        case heavy
        case soft
        case rigid

        public var displayName: String {
            switch self {
            case .light: "Light"
            case .medium: "Medium"
            case .heavy: "Heavy"
            case .soft: "Soft"
            case .rigid: "Rigid"
            }
        }
    }

    public enum NotificationStyle: String, CaseIterable {
        case success
        case warning
        case error

        public var displayName: String {
            switch self {
            case .success: "Success"
            case .warning: "Warning"
            case .error: "Error"
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
        AppSettings()
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
            case let .impact(style):
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
            case let .impact(style):
                impactGenerator(for: uiImpactStyle(from: style)).impactOccurred()

            case .selection:
                selectionGenerator.selectionChanged()

            case let .notification(style):
                notificationGenerator.notificationOccurred(uiNotificationStyle(from: style))

            case .buttonPress:
                let userIntensity = getUserIntensityStyle()
                impactGenerator(for: userIntensity).impactOccurred()

            case .tabSelection:
                selectionGenerator.selectionChanged()

            case let .dataRefresh(intensity):
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
            case .light: .light
            case .medium: .medium
            case .heavy: .heavy
            case .soft: .soft
            case .rigid: .rigid
            }
        }

        private func uiNotificationStyle(from style: NotificationStyle) -> UINotificationFeedbackGenerator.FeedbackType {
            switch style {
            case .success: .success
            case .warning: .warning
            case .error: .error
            }
        }
    #endif

    private func shouldGenerateHaptic(for _: HapticType) -> Bool {
        appSettings.appearanceSettings.hapticFeedback.isEnabled
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

public extension FlareHapticManager {
    struct HapticFeedbackModifier: ViewModifier {
        let hapticType: HapticType

        public func body(content: Content) -> some View {
            content
                .onTapGesture {
                    FlareHapticManager.shared.generate(hapticType)
                }
        }
    }
}

public extension View {
    func hapticFeedback(_ type: FlareHapticManager.HapticType) -> some View {
        modifier(FlareHapticManager.HapticFeedbackModifier(hapticType: type))
    }

    func hapticButtonPress() -> some View {
        hapticFeedback(.buttonPress)
    }
}
