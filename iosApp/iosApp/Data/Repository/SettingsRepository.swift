import Foundation

class SettingsRepository {
    static let shared = SettingsRepository()
    private init() {
        
    }
}
//
//@Observable
//class AppSettings {
//    var appearanceSettings: AppearanceSettings = AppearanceSettings()
//    var observer: NSKeyValueObservation?
//
//    init() {
//        observer = UserDefaults.standard.observe(\.appearanceSettings, options: [.new]) { [weak self] (defaults, change) in
//            if let newData = defaults.data(forKey: "AppearanceSettings") {
//                self?.appearanceSettings = ... // 解码 newData 为 AppearanceSettings 实例
//            }
//        }
//    }
//
//    deinit {
//        if let observer = observer {
//            UserDefaults.standard.removeObserver(observer, forKeyPath: "AppearanceSettings")
//        }
//    }
//}
