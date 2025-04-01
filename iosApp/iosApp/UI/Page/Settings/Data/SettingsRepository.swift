import Foundation
import SwiftUI

enum PreferredBrowser: String, CaseIterable, Codable {
    case inAppSafari
    case safari
}

@Observable
class OtherSettings: Codable {
    var preferredBrowser: PreferredBrowser = .inAppSafari
    var inAppBrowserReaderView: Bool = true
    
    init() {}
}

@Observable
class AppSettings {
    var appearanceSettings: AppearanceSettings = UserDefaults.standard.appearanceSettings {
        didSet {
            UserDefaults.standard.appearanceSettings = appearanceSettings
        }
    }
    
    var otherSettings: OtherSettings = UserDefaults.standard.otherSettings {
        didSet {
            UserDefaults.standard.otherSettings = otherSettings
        }
    }

    func update(newValue: AppearanceSettings) {
        withAnimation {
            appearanceSettings = newValue
        }
    }
    
    func updateOther(newValue: OtherSettings) {
        withAnimation {
            otherSettings = newValue
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
    
    var otherSettings: OtherSettings {
        get {
            if let data = UserDefaults.standard.object(forKey: "OtherSettings") as? Data {
                let decoder = JSONDecoder()
                if let settings = try? decoder.decode(OtherSettings.self, from: data) {
                    return settings
                }
            }
            return OtherSettings()
        }
        set {
            if let data = try? JSONEncoder().encode(newValue) {
                UserDefaults.standard.set(data, forKey: "OtherSettings")
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
