import Foundation

enum FLNewStateStorage {
    private enum Keys {
        static let menuState = "FLNewMenuState"
        static let lastTab = "FLNewLastTab"
    }

    // - Menu State
    static func saveMenuState(_ isOpen: Bool) {
        UserDefaults.standard.set(isOpen, forKey: Keys.menuState)
    }

    static func loadMenuState() -> Bool {
        UserDefaults.standard.bool(forKey: Keys.menuState)
    }

    // - Tab State
    static func saveLastTab(_ index: Int) {
        UserDefaults.standard.set(index, forKey: Keys.lastTab)
    }

    static func loadLastTab() -> Int {
        UserDefaults.standard.integer(forKey: Keys.lastTab)
    }

    // - Clear
    static func clearAll() {
        UserDefaults.standard.removeObject(forKey: Keys.menuState)
        UserDefaults.standard.removeObject(forKey: Keys.lastTab)
    }
}
