import UIKit

enum TimelineColumnPolicy {
    case adaptive
    case single

    func columnCount(for width: CGFloat) -> Int {
        guard case .adaptive = self, width.isFinite else { return 1 }
        let available = max(width - 32, 0)
        return max(Int((available + 8) / (320 + 8)), 1)
    }
}
