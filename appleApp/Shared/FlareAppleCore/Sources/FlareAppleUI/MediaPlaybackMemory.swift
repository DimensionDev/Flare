import Foundation

/// Playback positions live for this process and are shared by every account and page.
@MainActor
public final class MediaPlaybackMemory {
    public static let shared = MediaPlaybackMemory()
    private var positions: [String: Double] = [:]

    public init() {}

    public func position(for mediaURL: String) -> Double { positions[key(mediaURL)] ?? 0 }

    public func save(_ seconds: Double, for mediaURL: String) {
        guard seconds.isFinite, seconds >= 0 else { return }
        positions[key(mediaURL)] = seconds
    }

    private func key(_ url: String) -> String { URL(string: url)?.absoluteString ?? url }
}

/// A timeline keeps its own selection while progress is shared across timelines.
@MainActor
public final class TimelineMediaSelections {
    private struct Collection {
        let urls: [String]
        let select: (String) -> Void
    }

    private var collections: [String: Collection] = [:]
    private var selections: [[String]: String] = [:]

    public init() {}

    public func register(id: String, urls: [String], select: @escaping (String) -> Void) {
        collections[id] = Collection(urls: urls, select: select)
        if let selected = selections.removeValue(forKey: urls) { select(selected) }
    }

    public func remove(id: String) { collections.removeValue(forKey: id) }

    public func returned(urls: [String], selectedURL: String) {
        guard urls.contains(selectedURL) else { return }
        let matching = Array(collections.values).filter { $0.urls == urls }
        selections[urls] = matching.isEmpty ? selectedURL : nil
        for collection in matching {
            collection.select(selectedURL)
        }
    }
}
