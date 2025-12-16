import Foundation
import KotlinSharedUI

class Formatter: SwiftFormatter {
    private init() {}
    
    static let shared = Formatter()
    nonisolated func formatNumber(number: Int64) -> String {
        return Int(number)
            .formatted(
                .number
                    .notation(.compactName)
                    .grouping(.never)
                    .precision(.fractionLength(0...2))
            )
    }
}
