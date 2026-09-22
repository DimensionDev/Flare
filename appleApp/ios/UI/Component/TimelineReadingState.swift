import SwiftUI

/// A bookmark belongs to a page, and is released with that page's navigation entry.
@MainActor
final class TimelineReadingState {
    var position: TimelineCollectionView.ReadingPosition?
}

/// A page that replaces its selected child (home tabs, filters, search) owns these
/// states explicitly. Query bookmarks expire with their paging source.
@MainActor
final class TimelinePagePositions {
    private struct Entry {
        let state = TimelineReadingState()
        weak var owner: AnyObject?
        let expiresWithOwner: Bool
    }

    private var scope: String?
    private var entries: [String: Entry] = [:]

    func keep(_ keys: Set<String>) { entries = entries.filter { keys.contains($0.key) } }

    func state(for key: String, scope: String, owner: AnyObject? = nil) -> TimelineReadingState {
        if self.scope != scope {
            entries.removeAll()
            self.scope = scope
        }
        entries = entries.filter { !$0.value.expiresWithOwner || $0.value.owner != nil }
        if let entry = entries[key], entry.owner === owner { return entry.state }
        let entry = Entry(owner: owner, expiresWithOwner: owner != nil)
        entries[key] = entry
        return entry.state
    }
}

extension EnvironmentValues {
    @Entry var timelineAccountScope = ""
}
