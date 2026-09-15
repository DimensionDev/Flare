import SwiftUI
import KotlinSharedUI

public enum NetworkKind: Equatable {
    case wifi
    case cellular

    public var description: String {
        switch self {
        case .wifi:
            "Wi-Fi"
        case .cellular:
            "Cellular"
        }
    }
}

// @Entry evaluates default expressions on access. Keep Kotlin reference defaults stable.
private enum FlareEnvironmentDefaults {
    static let globalAppearance = GlobalAppearance.companion.Default
    static let timelineAppearance = TimelineAppearance.companion.Default
    static let aiConfig = AppSettings.AiConfig.companion.default
    static let translateConfig = AppSettings.TranslateConfig()
    static let appSettings = AppSettings.companion.default
}

public extension EnvironmentValues {
    @Entry var globalAppearance: GlobalAppearance = FlareEnvironmentDefaults.globalAppearance
    @Entry var timelineAppearance: TimelineAppearance = FlareEnvironmentDefaults.timelineAppearance
    @Entry var aiConfig: AppSettings.AiConfig = FlareEnvironmentDefaults.aiConfig
    @Entry var translateConfig: AppSettings.TranslateConfig = FlareEnvironmentDefaults.translateConfig
    @Entry var appSettings: AppSettings = FlareEnvironmentDefaults.appSettings
    @Entry var networkKind: NetworkKind = .cellular
    @Entry var isMultipleColumn = false
}
