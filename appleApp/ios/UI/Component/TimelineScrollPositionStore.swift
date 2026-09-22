import SwiftUI

/// Owned by the window, so replacing a page does not discard its reading position.
@MainActor
final class TimelineScrollPositionStore {
    private struct Entry {
        let position: TimelineCollectionView.ReadingPosition
        weak var owner: AnyObject?
        let expiresWithOwner: Bool

        var isAlive: Bool { !expiresWithOwner || owner != nil }
    }

    private var positions: [String: Entry] = [:]

    func removeAll() { positions.removeAll() }

    func removePositions(withPrefix prefix: String) {
        positions = positions.filter { !$0.key.hasPrefix(prefix) && $0.value.isAlive }
    }

    func save(_ position: TimelineCollectionView.ReadingPosition, for key: String, owner: AnyObject? = nil) {
        positions = positions.filter { $0.value.isAlive }
        positions[key] = Entry(position: position, owner: owner, expiresWithOwner: owner != nil)
    }

    subscript(key: String) -> TimelineCollectionView.ReadingPosition? {
        get {
            positions = positions.filter { $0.value.isAlive }
            return positions[key]?.position
        }
        set {
            if let newValue { save(newValue, for: key) }
            else { positions.removeValue(forKey: key) }
        }
    }
}

extension EnvironmentValues {
    @Entry var timelineAccountScope = ""
    @Entry var timelineScrollPositions: TimelineScrollPositionStore? = nil
}
