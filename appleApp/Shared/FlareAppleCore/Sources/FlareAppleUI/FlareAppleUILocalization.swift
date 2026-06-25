import Foundation
import FlareAppleCore

enum FlareAppleUILocalization {
    static let bundle = FlareAppleResource.bundle

    static func string(
        _ key: String,
        fallback: String? = nil,
        arguments: [CVarArg] = []
    ) -> String {
        let value = bundle.localizedString(forKey: key, value: fallback ?? key, table: nil)
        guard !arguments.isEmpty else { return value }
        return String(format: value, arguments: arguments)
    }
}
