import Foundation

/// Owns an imported file until the composer has finished copying it into the draft store.
public nonisolated final class ComposeMediaFile: @unchecked Sendable {
    public let url: URL
    private let ownedDirectory: URL?

    public init(copying source: URL) throws {
        let didStartAccessing = source.startAccessingSecurityScopedResource()
        defer {
            if didStartAccessing {
                source.stopAccessingSecurityScopedResource()
            }
        }
        guard try source.resourceValues(forKeys: [.isDirectoryKey]).isDirectory != true else {
            throw CocoaError(.fileReadUnknown)
        }
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("flare-compose-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let destination = directory.appendingPathComponent(source.lastPathComponent)
        do {
            try FileManager.default.copyItem(at: source, to: destination)
        } catch {
            try? FileManager.default.removeItem(at: directory)
            throw error
        }
        url = destination
        ownedDirectory = directory
    }

    /// Existing drafts remain owned by the shared draft store.
    public init(draftURL: URL) {
        url = draftURL
        ownedDirectory = nil
    }

    /// Clipboard providers can supply an already materialized Data value instead of a URL.
    public init(data: Data, fileName: String) throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("flare-compose-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let destination = directory.appendingPathComponent(URL(fileURLWithPath: fileName).lastPathComponent)
        do {
            try data.write(to: destination)
        } catch {
            try? FileManager.default.removeItem(at: directory)
            throw error
        }
        url = destination
        ownedDirectory = directory
    }

    deinit {
        if let ownedDirectory {
            try? FileManager.default.removeItem(at: ownedDirectory)
        }
    }
}
