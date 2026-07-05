import Foundation

enum FlareAppleCoreLocalization {
    static let bundle = Bundle.main

    static func string(
        _ key: String,
        fallback: String,
        arguments: [String] = []
    ) -> String {
        let value = bundle.localizedString(forKey: key, value: fallback, table: nil)
        guard !arguments.isEmpty else { return value }
        return String(format: value, arguments: arguments.map { $0 as CVarArg })
    }
}
