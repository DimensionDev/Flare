import Foundation

enum LocalizedStrings {
    static func string(_ key: String, fallback: String) -> String {
        Bundle.main.localizedString(forKey: key, value: fallback, table: nil)
    }
}
