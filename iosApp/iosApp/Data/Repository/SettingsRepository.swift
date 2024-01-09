import Foundation
import SwiftUI

@Observable
class AppSettings {
    var appearanceSettings: AppearanceSettings = UserDefaults.standard.appearanceSettings {
        didSet {
            UserDefaults.standard.appearanceSettings = appearanceSettings
        }
    }
    func update(newValue: AppearanceSettings) {
        withAnimation {
            appearanceSettings = newValue
        }
    }
}

extension UserDefaults {
    var appearanceSettings: AppearanceSettings {
        get {
            if let data = UserDefaults.standard.object(forKey: "AppearanceSettings") as? Data {
                let decoder = JSONDecoder()
                if let settings = try? decoder.decode(AppearanceSettings.self, from: data) {
                    return settings
                }
            }
            return AppearanceSettings()
        }
        set {
            if let data = try? JSONEncoder().encode(newValue) {
                UserDefaults.standard.set(data, forKey: "AppearanceSettings")
            }
        }
    }
}

private struct AppSettingsKey: EnvironmentKey {
    static let defaultValue = AppSettings()
}
extension EnvironmentValues {
    var appSettings: AppSettings {
        get { self[AppSettingsKey.self] }
        set { self[AppSettingsKey.self] = newValue }
    }
}
