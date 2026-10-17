// Paging inputs only; the window checks compile the production Swift scroll bridge unchanged.
import Foundation

public final class UiTimelineV2 {
    public let key: String
    public let height: Double
    public init(key: String, height: Double = 240) { self.key = key; self.height = height }
}
public final class PagingState<T> {
    public let rows: [T]
    public init(_ rows: [T]) { self.rows = rows }
}
public final class TimelineSnapshot {
    public let rows: [UiTimelineV2]
    public init(data: PagingState<UiTimelineV2>) { rows = data.rows }
    public func hasSameContent(other: TimelineSnapshot?) -> Bool {
        guard let other, rows.count == other.rows.count else { return false }
        return zip(rows, other.rows).allSatisfy { $0.key == $1.key && $0.height == $1.height }
    }
    public func readingIndexOf(key: String) -> Int32 { Int32(rows.firstIndex { $0.key == key } ?? -1) }
    public func restorationIndex(position: TimelineReadingPosition, refreshing: Bool) -> Int32 {
        refreshing ? -1 : readingIndexOf(key: position.itemKey)
    }
}
public final class TimelineReadingPosition {
    public let itemKey: String
    public let offset: Double
    public let requestId: Int64
    public let latest: Bool
    public init(itemKey: String, offset: Double, requestId: Int64, latest: Bool) {
        self.itemKey = itemKey; self.offset = offset; self.requestId = requestId; self.latest = latest
    }
}
@MainActor public protocol TimelineReadingState: AnyObject {
    var position: TimelineReadingPosition? { get }
    func updateViewport(itemKey: String?, offset: Double, atTop: Bool, interacting: Bool)
    func positionRestored(requestId: Int64)
    func cancelRestoration()
}
