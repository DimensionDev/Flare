import SwiftUI

/// Owned by the window, so replacing a page does not discard its reading position.
@MainActor
final class TimelineScrollPositionStore {
    private var positions: [String: TimelineCollectionView.ReadingPosition] = [:]

    func removeAll() { positions.removeAll() }

    func removePositions(withPrefix prefix: String) {
        positions = positions.filter { !$0.key.hasPrefix(prefix) }
    }

    subscript(key: String) -> TimelineCollectionView.ReadingPosition? {
        get { positions[key] }
        set { positions[key] = newValue }
    }
}

extension EnvironmentValues {
    @Entry var timelineAccountScope = ""
    @Entry var timelineScrollPositions: TimelineScrollPositionStore? = nil
}
