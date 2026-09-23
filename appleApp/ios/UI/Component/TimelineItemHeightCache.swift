import CoreGraphics

/// Render changes require a new measurement, but do not discard known geometry.
struct TimelineItemHeightCache {
    struct Geometry: Equatable {
        let widthInPixels: Int
        let multipleColumns: Bool
    }

    private struct Measurement {
        let geometry: Geometry
        let renderHash: Int32
        let height: CGFloat
    }

    private var items: [String: [Measurement]] = [:]

    func height(for id: String, geometry: Geometry, renderHash: Int32? = nil) -> CGFloat? {
        items[id]?.first {
            $0.geometry == geometry && (renderHash == nil || $0.renderHash == renderHash)
        }?.height
    }

    /// Returns whether the layout needs an update, independently of render state.
    mutating func store(_ height: CGFloat, for id: String, geometry: Geometry, renderHash: Int32) -> Bool {
        let previous = self.height(for: id, geometry: geometry)
        // Keep the geometry actually supplied to the layout when ignoring rounding
        // noise. Otherwise repeated sub-threshold updates could accumulate drift.
        let layoutHeight = previous.map { abs($0 - height) <= 1 ? $0 : height } ?? height
        var measurements = items[id] ?? []
        measurements.removeAll { $0.geometry == geometry }
        measurements.insert(Measurement(geometry: geometry, renderHash: renderHash, height: layoutHeight), at: 0)
        // Interactive window resizing and repeated likes must not grow the cache.
        items[id] = Array(measurements.prefix(2))
        return previous != layoutHeight
    }

    mutating func keep(_ ids: Set<String>) {
        items = items.filter { ids.contains($0.key) }
    }

    mutating func removeAll() { items.removeAll(keepingCapacity: true) }
}
