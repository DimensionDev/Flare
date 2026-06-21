import Foundation

private final class FlareAppleUIBundleToken {}

enum FlareAppleUILocalization {
    static let bundle = Bundle(for: FlareAppleUIBundleToken.self)

    static func string(
        _ key: String,
        fallback: String? = nil,
        arguments: [String] = []
    ) -> String {
        let value = bundle.localizedString(forKey: key, value: fallback ?? key, table: nil)
        guard !arguments.isEmpty else { return value }
        return String(format: value, arguments: arguments.map { $0 as CVarArg })
    }
}
