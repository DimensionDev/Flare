import SwiftUI

enum TimelineColumnPolicy {
    case adaptive
    case single

    func columnCount(for width: CGFloat) -> Int {
        guard case .adaptive = self, width.isFinite else { return 1 }
        let available = max(width - 32, 0)
        return max(Int((available + 8) / (320 + 8)), 1)
    }
}

struct ProfileLayoutPolicy: Equatable {
    let usesCard: Bool
    let profileWidth: CGFloat
    let columnSpacing: CGFloat

    init(width: CGFloat, division: CGRect? = nil, layoutDirection: LayoutDirection = .leftToRight) {
        // Classify before fold displacement so resizing a pane cannot switch modes.
        usesCard = TimelineColumnPolicy.adaptive.columnCount(for: width - 400 - 8) > 1
        if !usesCard, let division,
           division.width > 0, division.height > division.width,
           division.minX > 0, division.maxX < width {
            profileWidth = layoutDirection == .rightToLeft ? width - division.maxX : division.minX
            columnSpacing = division.width
        } else {
            profileWidth = 400
            columnSpacing = 8
        }
    }
}
