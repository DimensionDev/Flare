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
    private var pending: [String: Measurement] = [:]

    var hasPendingMeasurements: Bool { !pending.isEmpty }

    // Cell layout must not change geometry used by an in-flight offset compensation.
    @discardableResult
    mutating func stage(_ height: CGFloat, for id: String, geometry: Geometry, renderHash: Int32) -> Bool {
        if let previous = self.height(for: id, geometry: geometry, renderHash: renderHash), abs(previous - height) <= 1 {
            pending.removeValue(forKey: id)
            return false
        }
        pending[id] = Measurement(geometry: geometry, renderHash: renderHash, height: height)
        return true
    }

    mutating func applyPendingMeasurements() -> Bool {
        var changed = false
        for (id, measurement) in pending {
            if store(measurement.height, for: id, geometry: measurement.geometry, renderHash: measurement.renderHash) {
                changed = true
            }
        }
        pending.removeAll(keepingCapacity: true)
        return changed
    }

    func height(for id: String, geometry: Geometry, renderHash: Int32? = nil) -> CGFloat? {
        items[id]?.first {
            $0.geometry == geometry && (renderHash == nil || $0.renderHash == renderHash)
        }?.height
    }

    // Cells can reuse their latest measurement without publishing layout geometry.
    func measuredHeight(for id: String, geometry: Geometry, renderHash: Int32) -> CGFloat? {
        if let measurement = pending[id], measurement.geometry == geometry, measurement.renderHash == renderHash {
            return measurement.height
        }
        return height(for: id, geometry: geometry, renderHash: renderHash)
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
        pending = pending.filter { ids.contains($0.key) }
    }

    mutating func removeAll() {
        items.removeAll(keepingCapacity: true)
        pending.removeAll(keepingCapacity: true)
    }
}
