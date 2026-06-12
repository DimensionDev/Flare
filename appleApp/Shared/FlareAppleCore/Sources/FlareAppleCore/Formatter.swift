import Foundation
import KotlinSharedUI

public final class Formatter: SwiftFormatter, @unchecked Sendable {
    private init() {}

    public static let shared = Formatter()

    nonisolated public func formatNumber(number: Int64) -> String {
        Int(number)
            .formatted(
                .number
                    .notation(.compactName)
                    .grouping(.never)
                    .precision(.fractionLength(0...2))
            )
    }
}
