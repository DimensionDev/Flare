import Foundation
import KotlinSharedUI

public class PlatformFormatter: SwiftFormatter {
    private init() {}
    
    public static let shared = PlatformFormatter()
    nonisolated public func formatNumber(number: Int64) -> String {
        return Int(number)
            .formatted(
                .number
                    .notation(.compactName)
                    .grouping(.never)
                    .precision(.fractionLength(0...2))
            )
    }
}
