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

private struct GlobalAppearanceKey: EnvironmentKey {
    static let defaultValue = GlobalAppearance.companion.Default
}

private struct TimelineAppearanceKey: EnvironmentKey {
    static let defaultValue = TimelineAppearance.companion.Default
}

private struct AiConfigKey: EnvironmentKey {
    static let defaultValue = AppSettings.AiConfig.companion.default
}

private struct TranslateConfigKey: EnvironmentKey {
    static let defaultValue = AppSettings.TranslateConfig()
}

private struct AppSettingsKey: EnvironmentKey {
    static let defaultValue = AppSettings.companion.default
}

private struct NetworkKindKey: EnvironmentKey {
    static let defaultValue: NetworkKind = .cellular
}

private struct IsMultipleColumnKey: EnvironmentKey {
    static let defaultValue = false
}

public extension EnvironmentValues {
    var globalAppearance: GlobalAppearance {
        get { self[GlobalAppearanceKey.self] }
        set { self[GlobalAppearanceKey.self] = newValue }
    }

    var timelineAppearance: TimelineAppearance {
        get { self[TimelineAppearanceKey.self] }
        set { self[TimelineAppearanceKey.self] = newValue }
    }

    var aiConfig: AppSettings.AiConfig {
        get { self[AiConfigKey.self] }
        set { self[AiConfigKey.self] = newValue }
    }

    var translateConfig: AppSettings.TranslateConfig {
        get { self[TranslateConfigKey.self] }
        set { self[TranslateConfigKey.self] = newValue }
    }

    var appSettings: AppSettings {
        get { self[AppSettingsKey.self] }
        set { self[AppSettingsKey.self] = newValue }
    }

    var networkKind: NetworkKind {
        get { self[NetworkKindKey.self] }
        set { self[NetworkKindKey.self] = newValue }
    }

    var isMultipleColumn: Bool {
        get { self[IsMultipleColumnKey.self] }
        set { self[IsMultipleColumnKey.self] = newValue }
    }
}
