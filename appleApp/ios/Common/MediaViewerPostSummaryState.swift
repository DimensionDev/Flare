import Foundation

struct MediaViewerPostSummaryState {
    private(set) var height: CGFloat = 120
    private(set) var hasMeasurement = false
    private var isSettling = false
    private var deferredHeight: CGFloat?

    mutating func reset() {
        hasMeasurement = false
        isSettling = false
        deferredHeight = nil
    }

    @discardableResult
    mutating func measure(_ height: CGFloat) -> Bool {
        guard height.isFinite, height > 0 else { return false }
        let next = ceil(height)
        if isSettling {
            deferredHeight = next
            return false
        }
        let changed = !hasMeasurement || self.height != next
        self.height = next
        hasMeasurement = true
        return changed
    }

    mutating func beginPresentation() {
        isSettling = true
    }

    mutating func finishPresentation() -> CGFloat? {
        isSettling = false
        defer { deferredHeight = nil }
        return deferredHeight
    }
}
