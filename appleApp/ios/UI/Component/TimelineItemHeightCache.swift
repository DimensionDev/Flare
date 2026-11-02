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
    private var pending: [String: [Measurement]] = [:]

    var hasPendingMeasurements: Bool { !pending.isEmpty }
    var pendingItemIDs: [String] { Array(pending.keys) }

    func height(for id: String, geometry: Geometry, renderHash: Int32? = nil) -> CGFloat? {
        items[id]?.first {
            $0.geometry == geometry && (renderHash == nil || $0.renderHash == renderHash)
        }?.height
    }

    /// Cells may reuse their latest measurement before the layout adopts it.
    /// Layout sizing must keep reading `height` until the preserving update.
    func measuredHeight(for id: String, geometry: Geometry, renderHash: Int32) -> CGFloat? {
        pending[id]?.first { $0.geometry == geometry && $0.renderHash == renderHash }?.height
            ?? height(for: id, geometry: geometry, renderHash: renderHash)
    }

    mutating func stage(_ height: CGFloat, for id: String, geometry: Geometry, renderHash: Int32) {
        let previous = self.height(for: id, geometry: geometry)
        var measurements = pending[id] ?? []
        measurements.removeAll { $0.geometry == geometry }
        if let previous, abs(previous - height) <= 1 {
            // Updating render validity cannot move the layout. Publish it without
            // queuing another layout, including when a pending change is reverted.
            _ = store(height, for: id, geometry: geometry, renderHash: renderHash)
        } else {
            measurements.insert(Measurement(geometry: geometry, renderHash: renderHash, height: height), at: 0)
        }
        pending[id] = measurements.isEmpty ? nil : Array(measurements.prefix(2))
    }

    /// Invoke only after capturing the old layout's reading anchor. Measurements
    /// produced by the ensuing layout remain pending for the next coalesced batch.
    mutating func commitPendingMeasurements(validating isValid: (String, Geometry, Int32) -> Bool) -> Bool {
        let batch = pending
        pending = [:]
        var changed = false
        for (id, measurements) in batch {
            // Store oldest first so the newest geometry remains the first entry.
            for measurement in measurements.reversed() where isValid(id, measurement.geometry, measurement.renderHash) {
                let updated = store(measurement.height, for: id, geometry: measurement.geometry, renderHash: measurement.renderHash)
                changed = updated || changed
            }
        }
        return changed
    }

    /// Returns whether the layout needs an update, independently of render state.
    private mutating func store(_ height: CGFloat, for id: String, geometry: Geometry, renderHash: Int32) -> Bool {
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

    mutating func discardPendingMeasurements() {
        pending.removeAll(keepingCapacity: true)
    }

    mutating func removeAll() {
        items.removeAll(keepingCapacity: true)
        discardPendingMeasurements()
    }
}
